// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.*;

import java.util.Map;
import java.util.StringJoiner;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The FileStatusMap class represents a map that stores the status of files' analysis in a project.
 * It is mostly used to keep track of dirty regions. See {@link FileStatus} for the whole data.
 */
public final class FileStatusMap implements Disposable {
  private static final Logger LOG = Logger.getInstance(FileStatusMap.class);
  public static final String CHANGES_NOT_ALLOWED_DURING_HIGHLIGHTING = "PSI/document/model changes are not allowed during highlighting, " +
                                                                       "because it leads to the daemon unnecessary restarts. If you really do need to start write action " +
                                                                       "during the highlighting, you can pass `canChangeDocument=true` to the CodeInsightTestFixtureImpl#instantiateAndRun() " +
                                                                       "and accept the daemon unresponsiveness/blinking/slowdowns.";
  private final Project myProject;
  private final Map<@NotNull Document, FileStatus> myDocumentToStatusMap = new WeakHashMap<>(); // all dirty if absent
  // the ranges of last DocumentEvents united; used for "should I really remove invalid PSI highlighters from this range" heuristic
  private final /*non-static*/Key<RangeMarker> COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY = Key.create("COMPOSITE_DOCUMENT_CHANGE_KEY");
  private volatile boolean myAllowDirt = true;

  FileStatusMap(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
    // clear dangling references to PsiFiles/Documents. SCR#10358
    markAllFilesDirty("FileStatusMap dispose");
  }

  /**
   * @deprecated use {@link #getDirtyTextRange(Document, PsiFile, int)} instead
   */
  @Deprecated
  public static @Nullable("null means the file is clean") TextRange getDirtyTextRange(@NotNull Editor editor, int passId) {
    Document document = editor.getDocument();
    Project project = editor.getProject();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    return psiFile == null ? null : getDirtyTextRange(document, psiFile, passId);
  }

  public static @Nullable("null means the file is clean") TextRange getDirtyTextRange(@NotNull Document document, @NotNull PsiFile psiFile, int passId) {
    Project project = psiFile.getProject();
    FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap();
    TextRange dirtyScope = me.getFileDirtyScope(document, psiFile, passId);
    if (dirtyScope == null) return null;
    TextRange documentRange = TextRange.from(0, document.getTextLength());
    return documentRange.intersection(dirtyScope);
  }

  public void setErrorFoundFlag(@NotNull Project project, @NotNull Document document, boolean errorFound) {
    //GHP has found error. Flag is used by ExternalToolPass to decide whether to run or not
    synchronized(myDocumentToStatusMap) {
      myDocumentToStatusMap.computeIfAbsent(document, __->new FileStatus(project)).errorFound = errorFound;
    }
  }

  boolean wasErrorFound(@NotNull Document document) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && status.errorFound;
    }
  }

  private static final class FileStatus {
    private boolean defensivelyMarked; // The file was marked dirty without knowledge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
    private boolean wolfPassFinished;
    // if contains the special value "WHOLE_FILE_MARKER" then the corresponding range is (0, document length)
    private final Int2ObjectMap<RangeMarker> dirtyScopes = new Int2ObjectOpenHashMap<>(); // guarded by myDocumentToStatusMap
    private boolean errorFound;

    private FileStatus(@NotNull Project project) {
      markWholeFileDirty(project);
    }

    private void markWholeFileDirty(@NotNull Project project) {
      setDirtyScope(Pass.UPDATE_ALL, WHOLE_FILE_DIRTY_MARKER);
      setDirtyScope(Pass.EXTERNAL_TOOLS, WHOLE_FILE_DIRTY_MARKER);
      setDirtyScope(Pass.LOCAL_INSPECTIONS, WHOLE_FILE_DIRTY_MARKER);
      setDirtyScope(Pass.LINE_MARKERS, WHOLE_FILE_DIRTY_MARKER);
      setDirtyScope(Pass.SLOW_LINE_MARKERS, WHOLE_FILE_DIRTY_MARKER);
      setDirtyScope(Pass.INJECTED_GENERAL, WHOLE_FILE_DIRTY_MARKER);
      TextEditorHighlightingPassRegistrarEx registrar = (TextEditorHighlightingPassRegistrarEx) TextEditorHighlightingPassRegistrar.getInstance(project);
      for(DirtyScopeTrackingHighlightingPassFactory factory: registrar.getDirtyScopeTrackingFactories()) {
        setDirtyScope(factory.getPassId(), WHOLE_FILE_DIRTY_MARKER);
      }
    }

    private boolean allDirtyScopesAreNull() {
      for (RangeMarker o : dirtyScopes.values()) {
        if (o != null) return false;
      }
      return true;
    }

    private void combineScopesWith(@NotNull TextRange scope, int fileLength, @NotNull Document document) {
      dirtyScopes.replaceAll((__, oldScope) -> combineScopes(oldScope, scope, fileLength, document));
    }

    private static @NotNull RangeMarker combineScopes(@Nullable RangeMarker old, @NotNull TextRange scope, int textLength, @NotNull Document document) {
      if (scope.equalsToRange(0, textLength)) return WHOLE_FILE_DIRTY_MARKER;
      if (old == null) {
        return document.createRangeMarker(scope);
      }
      if (old == WHOLE_FILE_DIRTY_MARKER) return old;
      TextRange oldRange = old.getTextRange();
      TextRange union = scope.union(oldRange);
      if (old.isValid() && union.equals(oldRange)) {
        return old;
      }
      if (union.getEndOffset() > textLength) {
        union = union.intersection(new TextRange(0, textLength));
      }
      assert union != null;
      old.dispose();
      return document.createRangeMarker(union);
    }

    @Override
    @NonNls
    public String toString() {
      return
        (defensivelyMarked ? "defensivelyMarked = "+defensivelyMarked : "")
        +(wolfPassFinished ? "" : "; wolfPassFinished = "+wolfPassFinished)
        +(errorFound ? "; errorFound = "+errorFound : "")
        +(dirtyScopes.isEmpty() ? "" : "; dirtyScopes: ("+
        StringUtil.join(dirtyScopes.int2ObjectEntrySet(), e ->
          " pass: "+e.getIntKey()+" -> "+(e.getValue() == WHOLE_FILE_DIRTY_MARKER ? "Whole file" : e.getValue()), ";") +")");
    }

    private void setDirtyScope(int passId, @Nullable RangeMarker scope) {
      RangeMarker marker = dirtyScopes.get(passId);
      if (marker != scope) {
        if (marker != null) {
          marker.dispose();
        }
        dirtyScopes.put(passId, scope);
      }
    }
  }

  void markAllFilesDirty(@NotNull @NonNls Object reason) {
    assertAllowModifications();
    synchronized (myDocumentToStatusMap) {
      if (!myDocumentToStatusMap.isEmpty()) {
        log("Mark all dirty: ", reason);
      }
      myDocumentToStatusMap.clear();
    }
  }

  private void assertAllowModifications() {
    if (!myAllowDirt) {
      myAllowDirt = true; //give next test a chance
      throw new AssertionError(CHANGES_NOT_ALLOWED_DURING_HIGHLIGHTING);
    }
  }

  public void markFileUpToDate(@NotNull Document document, int passId) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.computeIfAbsent(document, __ -> new FileStatus(myProject));
      status.defensivelyMarked = false;
      if (passId == Pass.WOLF) {
        status.wolfPassFinished = true;
      }
      else if (status.dirtyScopes.containsKey(passId)) {
        status.setDirtyScope(passId, null);
      }
      if (status.allDirtyScopesAreNull()) {
        disposeDirtyDocumentRangeStorage(document);
      }
    }
  }

  TextRange getFileDirtyScopeForAllPassesCombined(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) {
        return null;
      }
      int start = Integer.MAX_VALUE;
      int end = Integer.MIN_VALUE;

      for (RangeMarker marker : status.dirtyScopes.values()) {
        if (marker != null && marker != WHOLE_FILE_DIRTY_MARKER && marker.isValid()) {
          start = Math.min(start, marker.getStartOffset());
          end = Math.max(end, marker.getEndOffset());
        }
      }
      return start == Integer.MAX_VALUE ? null : new TextRange(start, end);
    }
  }

  /**
   * @return null for up-to-date file, whole file for untouched or entirely dirty file, range(usually code block) for the dirty region (optimization)
   */
  public @Nullable TextRange getFileDirtyScope(@NotNull Document document, @Nullable PsiFile file, int passId) {
    RangeMarker marker;
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) {
        marker = WHOLE_FILE_DIRTY_MARKER;
      }
      else {
        if (status.defensivelyMarked) {
          status.markWholeFileDirty(myProject);
          status.defensivelyMarked = false;
        }
        assertRegisteredPass(passId, status);
        marker = status.dirtyScopes.get(passId);
      }
    }
    if (marker == null) {
      return null;
    }
    if (marker == WHOLE_FILE_DIRTY_MARKER) {
      return file == null ? null : file.getTextRange();
    }
    return marker.isValid() ? marker.getTextRange() : new TextRange(0, document.getTextLength());
  }

  private static void assertRegisteredPass(int passId, @NotNull FileStatus status) {
    if (!status.dirtyScopes.containsKey(passId)) throw new IllegalStateException("Unknown pass " + passId);
  }

  void markFileScopeDirtyDefensively(@NotNull PsiFile file, @NotNull @NonNls Object reason) {
    assertAllowModifications();
    log("Mark dirty file defensively: ",file.getName(),reason);
    // mark the whole file dirty in case no subsequent PSI events will come, but file requires re-highlighting nevertheless
    // e.g., in the case of quick typing/backspacing char
    synchronized(myDocumentToStatusMap){
      Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
      if (document == null) return;
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      status.defensivelyMarked = true;
    }
  }

  void markFileScopeDirty(@NotNull Document document, @NotNull TextRange scope, int fileLength, @NotNull @NonNls Object reason) {
    assertAllowModifications();
    log("Mark scope dirty: ",scope,reason);
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      if (status.defensivelyMarked) {
        status.defensivelyMarked = false;
      }
      status.combineScopesWith(scope, fileLength, document);
    }
  }

  public boolean allDirtyScopesAreNull(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && !status.defensivelyMarked && status.wolfPassFinished && status.allDirtyScopesAreNull();
    }
  }

  public String toString(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      return String.valueOf(myDocumentToStatusMap.get(document));
    }
  }

  @TestOnly
  public void assertAllDirtyScopesAreNull(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      assert status != null && !status.defensivelyMarked && status.wolfPassFinished && status.allDirtyScopesAreNull() : status;
    }
  }

  /**
   * (Dis)Allows file modifications during highlighting testing. Might be useful to catch unexpected modification requests.
   * @return the old value: true if modifications were allowed, false otherwise
   */
  @TestOnly
  boolean allowDirt(boolean allow) {
    boolean old = myAllowDirt;
    myAllowDirt = allow;
    return old;
  }

  private static final RangeMarker WHOLE_FILE_DIRTY_MARKER =
    new RangeMarker() {
      @Override
      public @NotNull Document getDocument() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getStartOffset() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getEndOffset() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public void setGreedyToLeft(boolean greedy) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void setGreedyToRight(boolean greedy) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isGreedyToRight() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isGreedyToLeft() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void dispose() {
        // ignore
      }

      @Override
      public <T> T getUserData(@NotNull Key<T> key) {
        return null;
      }

      @Override
      public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NonNls String toString() {
        return "WHOLE_FILE";
      }
    };

  // logging
  private static final ConcurrentMap<Thread, Integer> threads = CollectionFactory.createConcurrentWeakMap();

  private static int getThreadNum() {
    return threads.computeIfAbsent(Thread.currentThread(), thread -> threads.size());
  }

  public static void log(@NonNls Object @NotNull ... info) {
    if (LOG.isDebugEnabled()) {
      StringJoiner joiner = new StringJoiner(", ", " ".repeat(getThreadNum() * 4) + "[", "]\n");
      for (Object o : info) {
        joiner.add(String.valueOf(o));
      }
      LOG.debug(joiner.toString());
    }
  }

  // store any Document change and combine it with every previous one to form one big dirty change
  void addDocumentDirtyRange(@NotNull DocumentEvent event) {
    Document document = event.getDocument();
    RangeMarker oldRange = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    if (oldRange != WHOLE_FILE_DIRTY_MARKER && oldRange != null && oldRange.isValid() && oldRange.getTextRange().containsRange(event.getOffset(), event.getOffset()+event.getNewLength())) {
      // optimisation: the change is inside the RangeMarker which should take care of the change by itself
      return;
    }
    int textLength = document.getTextLength();
    RangeMarker combined = FileStatus.combineScopes(oldRange, new TextRange(event.getOffset(), Math.min(event.getOffset() + event.getNewLength(), textLength)), textLength, document);
    if (combined != WHOLE_FILE_DIRTY_MARKER) {
      combined.setGreedyToRight(true);
      combined.setGreedyToLeft(true);
    }
    document.putUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY, combined);
  }

  // get one big dirty region united from all small document changes before highlighting finished
  @NotNull
  TextRange getCompositeDocumentDirtyRange(@NotNull Document document) {
    RangeMarker change = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    return change == WHOLE_FILE_DIRTY_MARKER ? new TextRange(0, document.getTextLength()) :
           change == null || !change.isValid() ? TextRange.EMPTY_RANGE :
           change.getTextRange();
  }
  @ApiStatus.Internal
  public void disposeDirtyDocumentRangeStorage(@NotNull Document document) {
    RangeMarker marker = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    if (marker != null) {
      marker.dispose();
      document.putUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY, null);
    }
  }

}

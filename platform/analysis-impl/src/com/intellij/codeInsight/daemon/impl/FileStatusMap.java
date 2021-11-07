// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentMap;

public final class FileStatusMap implements Disposable {
  private static final Logger LOG = Logger.getInstance(FileStatusMap.class);
  public static final String CHANGES_NOT_ALLOWED_DURING_HIGHLIGHTING = "PSI/document/model changes are not allowed during highlighting";
  private final Project myProject;
  private final Map<@NotNull Document,FileStatus> myDocumentToStatusMap = CollectionFactory.createWeakMap(); // all dirty if absent
  private volatile boolean myAllowDirt = true;

  FileStatusMap(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
    // clear dangling references to PsiFiles/Documents. SCR#10358
    markAllFilesDirty("FileStatusMap dispose");
  }

  @Nullable("null means the file is clean")
  public static TextRange getDirtyTextRange(@NotNull Editor editor, int passId) {
    Document document = editor.getDocument();

    FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(editor.getProject()).getFileStatusMap();
    TextRange dirtyScope = me.getFileDirtyScope(document, passId);
    if (dirtyScope == null) return null;
    TextRange documentRange = TextRange.from(0, document.getTextLength());
    return documentRange.intersection(dirtyScope);
  }

  public void setErrorFoundFlag(@NotNull Project project, @NotNull Document document, boolean errorFound) {
    //GHP has found error. Flag is used by ExternalToolPass to decide whether to run or not
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        if (!errorFound) return;
        status = new FileStatus(project);
        myDocumentToStatusMap.put(document, status);
      }
      status.errorFound = errorFound;
    }
  }

  boolean wasErrorFound(@NotNull Document document) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && status.errorFound;
    }
  }

  private static final class FileStatus {
    private boolean defensivelyMarked; // file marked dirty without knowledge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
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
      TextEditorHighlightingPassRegistrarEx registrar = (TextEditorHighlightingPassRegistrarEx) TextEditorHighlightingPassRegistrar.getInstance(project);
      for(DirtyScopeTrackingHighlightingPassFactory factory: registrar.getDirtyScopeTrackingFactories()) {
        setDirtyScope(factory.getPassId(), WHOLE_FILE_DIRTY_MARKER);
      }
    }

    private boolean allDirtyScopesAreNull() {
      for (Object o : dirtyScopes.values()) {
        if (o != null) return false;
      }
      return true;
    }

    private void combineScopesWith(@NotNull TextRange scope, int fileLength, @NotNull Document document) {
      dirtyScopes.replaceAll((__, oldScope) -> {
        RangeMarker newScope = combineScopes(oldScope, scope, fileLength, document);
        if (newScope != oldScope && oldScope != null) {
          oldScope.dispose();
        }
        return newScope;
      });
    }

    @NotNull
    private static RangeMarker combineScopes(@Nullable RangeMarker old, @NotNull TextRange scope, int textLength, @NotNull Document document) {
      if (old == null) {
        if (scope.equalsToRange(0, textLength)) return WHOLE_FILE_DIRTY_MARKER;
        return document.createRangeMarker(scope);
      }
      if (old == WHOLE_FILE_DIRTY_MARKER) return old;
      TextRange oldRange = TextRange.create(old);
      TextRange union = scope.union(oldRange);
      if (old.isValid() && union.equals(oldRange)) {
        return old;
      }
      if (union.getEndOffset() > textLength) {
        union = union.intersection(new TextRange(0, textLength));
      }
      assert union != null;
      return document.createRangeMarker(union);
    }

    @Override
    public String toString() {
      @NonNls StringBuilder s = new StringBuilder();
      s.append("defensivelyMarked = ").append(defensivelyMarked);
      s.append("; wolfPassFinfished = ").append(wolfPassFinished);
      s.append("; errorFound = ").append(errorFound);
      s.append("; dirtyScopes: (");
      dirtyScopes.forEach((passId, rangeMarker) ->
        s.append(" pass: ").append(passId).append(" -> ").append(rangeMarker == WHOLE_FILE_DIRTY_MARKER ? "Whole file" : rangeMarker).append(";")
      );
      s.append(")");
      return s.toString();
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
    }
  }

  TextRange getFileDirtyScopeForAllPassesCombined(@NotNull Document document) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (!ProblemHighlightFilter.shouldHighlightFile(file)) return null;

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
   * @return null for up-to-date file, whole file for untouched or entirely dirty file, range(usually code block) for dirty region (optimization)
   */
  @Nullable
  public TextRange getFileDirtyScope(@NotNull Document document, int passId) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (!ProblemHighlightFilter.shouldHighlightFile(file)) return null;

    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) {
        return file == null ? null : file.getTextRange();
      }
      if (status.defensivelyMarked) {
        status.markWholeFileDirty(myProject);
        status.defensivelyMarked = false;
      }
      assertRegisteredPass(passId, status);
      RangeMarker marker = status.dirtyScopes.get(passId);
      return marker == null ? null : marker.isValid() ? TextRange.create(marker) : new TextRange(0, document.getTextLength());
    }
  }

  private static void assertRegisteredPass(int passId, @NotNull FileStatus status) {
    if (!status.dirtyScopes.containsKey(passId)) throw new IllegalStateException("Unknown pass " + passId);
  }

  void markFileScopeDirtyDefensively(@NotNull PsiFile file, @NotNull @NonNls Object reason) {
    assertAllowModifications();
    log("Mark dirty file defensively: ",file.getName(),reason);
    // mark whole file dirty in case no subsequent PSI events will come, but file requires rehighlighting nevertheless
    // e.g. in the case of quick typing/backspacing char
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
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (!ProblemHighlightFilter.shouldHighlightFile(file)) return true;

    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && !status.defensivelyMarked && status.wolfPassFinished && status.allDirtyScopesAreNull();
    }
  }

  @TestOnly
  public void assertAllDirtyScopesAreNull(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      assert status != null && !status.defensivelyMarked && status.wolfPassFinished && status.allDirtyScopesAreNull() : status;
    }
  }

  @TestOnly
  void allowDirt(boolean allow) {
    myAllowDirt = allow;
  }

  private static final RangeMarker WHOLE_FILE_DIRTY_MARKER =
    new RangeMarker() {
      @NotNull
      @Override
      public Document getDocument() {
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
}

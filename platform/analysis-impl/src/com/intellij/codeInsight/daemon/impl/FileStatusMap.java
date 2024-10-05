// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
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
  private final Map<@NotNull Document, @NotNull FileStatus> myDocumentToStatusMap = new WeakHashMap<>(); // all dirty if absent; guarded by myDocumentToStatusMap
  // the ranges of last DocumentEvents united; used for "should I really remove invalid PSI highlighters from this range" heuristic
  private final /*non-static*/Key<RangeMarker> COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY = Key.create("COMPOSITE_DOCUMENT_CHANGE_KEY");
  private volatile boolean myAllowDirt = true;

  @ApiStatus.Internal
  public FileStatusMap(@NotNull Project project) {
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
    if (project == null) return null;

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
      myDocumentToStatusMap.computeIfAbsent(document, __->new FileStatus(project)).setErrorFound(errorFound);
    }
  }

  @ApiStatus.Internal
  public boolean wasErrorFound(@NotNull Document document) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && status.isErrorFound();
    }
  }

  @ApiStatus.Internal
  public void markAllFilesDirty(@NotNull @NonNls Object reason) {
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
      status.setDefensivelyMarked(false);
      if (passId == Pass.WOLF) {
        status.setWolfPassFinished();
      }
      else if (status.containsDirtyScope(passId)) {
        status.setDirtyScope(passId, null);
      }
      if (status.allDirtyScopesAreNull()) {
        disposeDirtyDocumentRangeStorage(document);
      }
    }
  }

  @ApiStatus.Internal
  public TextRange getFileDirtyScopeForAllPassesCombined(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) {
        return null;
      }
      int start = Integer.MAX_VALUE;
      int end = Integer.MIN_VALUE;

      for (RangeMarker marker : status.getAllDirtyScopes()) {
        if (marker != null && marker != WholeFileDirtyMarker.INSTANCE && marker.isValid()) {
          TextRange markerRange = marker.getTextRange();
          start = Math.min(start, markerRange.getStartOffset());
          end = Math.max(end, markerRange.getEndOffset());
        }
      }
      return start == Integer.MAX_VALUE ? null : new TextRange(start, end);
    }
  }

  /**
   * @return null for up-to-date file, whole file for untouched or entirely dirty file, range(usually code block) for the dirty region (optimization)
   */
  public @Nullable TextRange getFileDirtyScope(@NotNull Document document, @NotNull PsiFile psiFile, int passId) {
    RangeMarker marker;
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) {
        marker = WholeFileDirtyMarker.INSTANCE;
      }
      else {
        if (status.isDefensivelyMarked()) {
          status.markWholeFileDirty(myProject);
          status.setDefensivelyMarked(false);
        }
        assertPassIsRegistered(passId, status);
        marker = status.getDirtyScope(passId);
      }
    }
    if (marker == null) {
      return null;
    }
    if (marker == WholeFileDirtyMarker.INSTANCE) {
      return psiFile.getTextRange();
    }
    return marker.isValid() ? marker.getTextRange() : new TextRange(0, document.getTextLength());
  }

  private static void assertPassIsRegistered(int passId, @NotNull FileStatus status) {
    if (!status.containsDirtyScope(passId)) {
      throw new IllegalStateException("Unknown pass " + passId);
    }
  }

  @ApiStatus.Internal
  public void markFileScopeDirtyDefensively(@NotNull Document document, @NotNull @NonNls Object reason) {
    assertAllowModifications();
    log("Mark dirty file defensively: ",document,reason);
    // mark the whole file dirty in case no subsequent PSI events will come, but file requires re-highlighting nevertheless
    // e.g., in the case of quick typing/backspacing char
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      status.setDefensivelyMarked(true);
    }
  }

  @ApiStatus.Internal
  public void markWholeFileScopeDirty(@NotNull Document document, @NotNull @NonNls Object reason) {
    combineDirtyScopes(document, FileStatus.WHOLE_FILE_TEXT_RANGE, reason);
  }

  @ApiStatus.Internal
  public void markScopeDirty(@NotNull Document document, @NotNull TextRange scope, @NotNull @NonNls Object reason) {
    ApplicationManager.getApplication().assertIsNonDispatchThread(); // assert dirty scope updates happen in BGT only, see IJPL-163033
    ApplicationManager.getApplication().assertReadAccessAllowed();
    combineDirtyScopes(document, scope, reason);
  }
  private void combineDirtyScopes(@NotNull Document document, @NotNull TextRange scope, @NonNls @NotNull Object reason) {
    assertAllowModifications();
    log("Mark scope dirty: ", scope, reason);
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return;
      if (status.isDefensivelyMarked()) {
        status.setDefensivelyMarked(false);
      }
      status.combineScopesWith(scope, document);
    }
  }

  public boolean allDirtyScopesAreNull(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && !status.isDefensivelyMarked() && status.isWolfPassFinished() && status.allDirtyScopesAreNull();
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
      assert status != null && !status.isDefensivelyMarked() && status.isWolfPassFinished() && status.allDirtyScopesAreNull() : status;
    }
  }

  /**
   * Runs {@code runnable} while (Dis)Allowing file modifications during highlighting testing. Might be useful to catch unexpected modification requests.
   */
  @TestOnly
  @ApiStatus.Internal
  public <E extends Exception> void runAllowingDirt(boolean allowDirt, @NotNull ThrowableRunnable<E> runnable) throws E {
    boolean old = myAllowDirt;
    try {
      myAllowDirt = allowDirt;
      runnable.run();
    }
    finally {
      myAllowDirt = old;
    }
  }

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
  @ApiStatus.Internal
  public void addDocumentDirtyRange(@NotNull DocumentEvent event) {
    Document document = event.getDocument();
    RangeMarker oldRange = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    if (oldRange != WholeFileDirtyMarker.INSTANCE && oldRange != null && oldRange.isValid() && oldRange.getTextRange().containsRange(event.getOffset(), event.getOffset()+event.getNewLength())) {
      // optimisation: the change is inside the RangeMarker which should take care of the change by itself
      return;
    }
    TextRange scope = new TextRange(event.getOffset(), Math.min(event.getOffset() + event.getNewLength(), document.getTextLength()));
    RangeMarker combined = oldRange == WholeFileDirtyMarker.INSTANCE || event.isWholeTextReplaced() ||
                           scope.getStartOffset() == 0 && scope.getEndOffset() == document.getTextLength() ? WholeFileDirtyMarker.INSTANCE :
                           FileStatus.combineScopes(oldRange, scope, document);
    if (combined != WholeFileDirtyMarker.INSTANCE) {
      combined.setGreedyToRight(true);
      combined.setGreedyToLeft(true);
    }
    document.putUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY, combined);
  }

  // get one big dirty region united from all small document changes before highlighting finished
  @NotNull
  @ApiStatus.Internal
  public TextRange getCompositeDocumentDirtyRange(@NotNull Document document) {
    RangeMarker change = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    return change == WholeFileDirtyMarker.INSTANCE ? new TextRange(0, document.getTextLength()) :
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

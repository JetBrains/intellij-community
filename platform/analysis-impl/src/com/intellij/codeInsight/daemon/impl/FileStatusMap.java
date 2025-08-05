// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextUtil;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.*;

import java.util.Collection;
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
  private final FileStatusMapState myDocumentToStatusMap;
  // the ranges of last DocumentEvents united; used for "should I really remove invalid PSI highlighters from this range" heuristic
  private final /*non-static*/Key<RangeMarker> COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY = Key.create("COMPOSITE_DOCUMENT_CHANGE_KEY");
  private volatile boolean myAllowDirt = true;

  @ApiStatus.Internal
  public FileStatusMap(@NotNull Project project) {
    myProject = project;
    myDocumentToStatusMap = CodeInsightContexts.isSharedSourceSupportEnabled(project) ? new MultiverseFileStatusMapState(project)
                                                                                       : new ClassicFileStatusMapState(project);
  }

  @Override
  public void dispose() {
    // clear dangling references to PsiFiles/Documents. SCR#10358
    markAllFilesDirty("FileStatusMap dispose");
  }

  /**
   * @deprecated use {@link #getDirtyTextRange(Document, CodeInsightContext, PsiFile, int)} instead
   */
  @Deprecated
  public static @Nullable("null means the file is clean") TextRange getDirtyTextRange(@NotNull Editor editor, int passId) {
    Document document = editor.getDocument();
    Project project = editor.getProject();
    if (project == null) return null;

    CodeInsightContext context = EditorContextManager.getEditorContext(editor, project);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document, context);
    return psiFile == null ? null : getDirtyTextRange(document, context, psiFile, passId);
  }

  public static @Nullable("null means the file is clean") TextRange getDirtyTextRange(@NotNull Document document,
                                                                                      @NotNull PsiFile psiFile,
                                                                                      int passId) {
    CodeInsightContext context = CodeInsightContextUtil.getCodeInsightContext(psiFile);
    return getDirtyTextRange(document, context, psiFile, passId);
  }

  @ApiStatus.Experimental
  public static @Nullable("null means the file is clean") TextRange getDirtyTextRange(@NotNull Document document,
                                                                                      @NotNull CodeInsightContext context,
                                                                                      @NotNull PsiFile psiFile,
                                                                                      int passId) {
    Project project = psiFile.getProject();
    FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap();
    TextRange dirtyScope = me.getFileDirtyScope(document, context, psiFile, passId);
    if (dirtyScope == null) return null;
    TextRange documentRange = TextRange.from(0, document.getTextLength());
    return documentRange.intersection(dirtyScope);
  }

  /** it's here for compatibility */
  public void setErrorFoundFlag(@NotNull Project project, @NotNull Document document, boolean errorFound) {
    setErrorFoundFlag(document, CodeInsightContexts.anyContext(), errorFound);
  }

  @ApiStatus.Experimental
  public void setErrorFoundFlag(@NotNull Document document, @NotNull CodeInsightContext context, boolean errorFound) {
    //GHP has found error. Flag is used by ExternalToolPass to decide whether to run or not
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.getOrCreateStatus(document, context);
      status.setErrorFound(errorFound);
    }
  }

  @ApiStatus.Internal
  public boolean wasErrorFound(@NotNull Document document, @NotNull CodeInsightContext context) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.getStatusOrNull(document, context);
      return status != null && status.isErrorFound();
    }
  }

  @ApiStatus.Internal
  public void markAllFilesDirty(@NotNull @NonNls Object reason) {
    assertAllowModifications();
    synchronized (myDocumentToStatusMap) {
      if (!myDocumentToStatusMap.isEmpty()) {
        log(null, "Mark all dirty: ", reason, null);
      }
      myDocumentToStatusMap.clear();
    }
  }

  @ApiStatus.Internal
  @TestOnly
  public void assertFileStatusScopeIsNull(Document document, @NotNull CodeInsightContext context, int passId) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.getStatusOrNull(document, context);
      assert status != null && status.getDirtyScope(passId) == null : status;
    }
  }

  private void assertAllowModifications() {
    if (!myAllowDirt) {
      myAllowDirt = true; //give next test a chance
      throw new AssertionError(CHANGES_NOT_ALLOWED_DURING_HIGHLIGHTING);
    }
  }

  // used in plugins
  public void markFileUpToDate(@NotNull Document document, int passId) {
    markFileUpToDate(document, CodeInsightContexts.anyContext(), passId, null);
  }

  @ApiStatus.Experimental
  public void markFileUpToDate(@NotNull Document document, @NotNull CodeInsightContext context, int passId, ProgressIndicator indicator) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.getOrCreateStatus(document, context);
      status.setDefensivelyMarked(false);
      if (passId == Pass.WOLF) {
        status.setWolfPassFinished();
      }
      else if (status.containsDirtyScope(passId)) {
        RangeMarker wasScope = status.getDirtyScope(passId);
        if (wasScope != null) {
          if (LOG.isTraceEnabled()) {
            LOG.trace("markFileUpToDate: " + passId +" (was "+wasScope+"); indicator:"+indicator);
          }
        }
        status.setDirtyScope(passId, null);
      }
    }
  }

  @ApiStatus.Internal
  public @Nullable TextRange getFileDirtyScopeForAllPassesCombined(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      Collection<FileStatus> statuses = myDocumentToStatusMap.getFileStatuses(document);
      if (statuses.isEmpty()) {
        return null;
      }

      int start = Integer.MAX_VALUE;
      int end = Integer.MIN_VALUE;

      for (FileStatus status : statuses) {
        for (RangeMarker marker : status.getAllDirtyScopes()) {
          if (marker != null && marker != WholeFileDirtyMarker.INSTANCE && marker.isValid()) {
            TextRange markerRange = marker.getTextRange();
            start = Math.min(start, markerRange.getStartOffset());
            end = Math.max(end, markerRange.getEndOffset());
          }
        }
      }
      return start == Integer.MAX_VALUE ? null : new TextRange(start, end);
    }
  }

  /**
   * @return null for up-to-date file, whole file for untouched or entirely dirty file, range(usually code block) for the dirty region (optimization)
   */
  public @Nullable TextRange getFileDirtyScope(@NotNull Document document, @NotNull PsiFile psiFile, int passId) {
    CodeInsightContext context = CodeInsightContextUtil.getCodeInsightContext(psiFile);
    return getFileDirtyScope(document, context, psiFile, passId);
  }

  /**
   * @return null for up-to-date file, whole file for untouched or entirely dirty file, range(usually code block) for the dirty region (optimization)
   */
  @ApiStatus.Experimental
  public @Nullable TextRange getFileDirtyScope(@NotNull Document document,
                                               @NotNull CodeInsightContext context,
                                               @NotNull PsiFile psiFile,
                                               int passId) {
    RangeMarker marker;
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.getStatusOrNull(document, context);
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
    if (marker == WholeFileDirtyMarker.INSTANCE) {
      return psiFile.getTextRange();
    }
    TextRange compositeDocumentDirtyRange = getCompositeDocumentDirtyRange(document);
    if (marker == null) {
      return compositeDocumentDirtyRange;
    }
    return marker.isValid() ? compositeDocumentDirtyRange == null ? marker.getTextRange() : compositeDocumentDirtyRange.union(marker.getTextRange()) : new TextRange(0, document.getTextLength());
  }

  private static void assertPassIsRegistered(int passId, @NotNull FileStatus status) {
    if (!status.containsDirtyScope(passId)) {
      throw new IllegalStateException("Unknown pass " + passId);
    }
  }

  @ApiStatus.Internal
  public void markFileScopeDirtyDefensively(@NotNull Document document, @NotNull @NonNls Object reason) {
    assertAllowModifications();
    log(document, "Mark dirty file defensively: ", reason, null);
    // mark the whole file dirty in case no subsequent PSI events will come, but file requires re-highlighting nevertheless
    // e.g., in the case of quick typing/backspacing char
    synchronized (myDocumentToStatusMap) {
      for (FileStatus status : myDocumentToStatusMap.getFileStatuses(document)) {
        status.setDefensivelyMarked(true);
      }
    }
  }

  @ApiStatus.Internal
  public void markWholeFileScopeDirty(@NotNull Document document, @NotNull @NonNls Object reason) {
    combineDirtyScopes(document, FileStatus.WHOLE_FILE_TEXT_RANGE, reason);
  }

 @ApiStatus.Internal
  public void markScopeDirty(@NotNull Document document,
                             @NotNull TextRange scope,
                             @NotNull @NonNls Object reason) {
    ApplicationManager.getApplication().assertIsNonDispatchThread(); // assert dirty scope updates happen in BGT only, see IJPL-163033
    ApplicationManager.getApplication().assertReadAccessAllowed();
    combineDirtyScopes(document, scope, reason);
  }

  private void combineDirtyScopes(@NotNull Document document, @NotNull TextRange scope, @NonNls @NotNull Object reason) {
    assertAllowModifications();
    log(document, "Mark scope dirty: ", reason, scope);
    synchronized(myDocumentToStatusMap) {
      for (FileStatus status : myDocumentToStatusMap.getFileStatuses(document)) {
        if (status.isDefensivelyMarked()) {
          status.setDefensivelyMarked(false);
        }
        status.combineScopesWith(scope, document);
      }
    }
  }

  // todo remove? it's unused in plugins
  @SuppressWarnings("unused")
  public boolean allDirtyScopesAreNull(@NotNull Document document) {
    return allDirtyScopesAreNull(document, CodeInsightContexts.anyContext());
  }

  // todo IJPL-339 do we need context here?
  @ApiStatus.Experimental
  public boolean allDirtyScopesAreNull(@NotNull Document document, @NotNull CodeInsightContext context) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.getStatusOrNull(document, context);
      return status != null && !status.isDefensivelyMarked() && status.isWolfPassFinished() && status.allDirtyScopesAreNull();
    }
  }

  public String toString(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      return myDocumentToStatusMap.toString(document);
    }
  }

  @TestOnly
  public void assertAllDirtyScopesAreNull(@NotNull Document document) {
    synchronized (myDocumentToStatusMap) {
      for (FileStatus status : myDocumentToStatusMap.getFileStatuses(document)) {
        assert status != null && !status.isDefensivelyMarked() && status.isWolfPassFinished() && status.allDirtyScopesAreNull() : status;
      }
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

  static final RangeMarker WHOLE_FILE_DIRTY_MARKER =
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

  private static void log(@Nullable Document document, @NonNls @NotNull String msg, @NonNls @NotNull Object reason, @NonNls Object additionalInfo) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(StringUtil.repeatSymbol(' ', getThreadNum() * 4)
                + (document == null ? "" : document +"; ")
                + msg
                + reason
                + (additionalInfo == null ? "": "; "+additionalInfo)
      );
    }
  }

  // store any Document change and combine it with every previous one to form one big dirty change
  @ApiStatus.Internal
  public void addDocumentCompositeDirtyRange(@NotNull DocumentEvent event) {
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

  /**
   * get one big dirty region united from all small document changes before highlighting finished
   * or null if no changes are recorded
   */
  @ApiStatus.Internal
  public TextRange getCompositeDocumentDirtyRange(@NotNull Document document) {
    RangeMarker change = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    return change == WholeFileDirtyMarker.INSTANCE ? new TextRange(0, document.getTextLength()) :
           change == null || !change.isValid() ? null :
           change.getTextRange();
  }

  @ApiStatus.Internal
  public void disposeDirtyDocumentRangeStorage(@NotNull Document document) {
    RangeMarker marker = document.getUserData(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY);
    if (marker != null) {
      marker.dispose();
      ((UserDataHolderEx)document).replace(COMPOSITE_DOCUMENT_DIRTY_RANGE_KEY, marker, null);
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.InjectionUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

//todo listen & notifyListeners readonly events?
public final class PsiDocumentManagerImpl extends PsiDocumentManagerBase {
  public PsiDocumentManagerImpl(@NotNull Project project) {
    super(project);

    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.getEventMulticaster().addDocumentListener(this, this);
    ((EditorEventMulticasterImpl)editorFactory.getEventMulticaster()).addPrioritizedDocumentListener(new PriorityEventCollector(), this);
  }

  @Override
  public @Nullable PsiFile getPsiFile(@NotNull Document document) {
    final PsiFile psiFile = super.getPsiFile(document);
    if (myUnitTestMode) {
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile != null) {
        assertFileIsFromCorrectProject(virtualFile);
      }
    }
    return psiFile;
  }

  @Override
  public void assertFileIsFromCorrectProject(@NotNull VirtualFile virtualFile) {
    if (myUnitTestMode && virtualFile.isValid()) {
      Collection<Project> projects = ProjectLocator.getInstance().getProjectsForFile(virtualFile);
      boolean isMyProject = projects.isEmpty() || projects.contains(myProject)
                            // set aside the use-case for lazy developers who just don't care to retrieve the correct project for the file
                            // and use DefaultProjectFactory.getDefaultProject() because why bother
                            || myProject.isDefault();
      if (!isMyProject) {
        Logger.getInstance(getClass()).error(
          "Trying to get PSI for a file that is not included in the project model of this project.\n" +
          "virtualFile=" + virtualFile + ";\n" +
          "project=" + myProject + " (" + myProject.getBasePath() + ");\n" +
          "The file actually belongs to\n  " + StringUtil.join(projects, p -> p + " (" + p.getBasePath() + ")", "\n  ") + "\n" +
          "Note:\n" +
          "This error happens if ProjectLocatorImpl#isUnder(project, file) returns false, which means that the file is not included\n" +
          "in the project model of the project. And usually, projects should not touch such files.\n" +
          "Feel free to reach out to IntelliJ Code Platform team if you have questions or concerns.");
      }
    }
  }

  @Override
  protected void beforeDocumentChangeOnUnlockedDocument(final @NotNull FileViewProvider viewProvider) {
    PostprocessReformattingAspect.getInstance(myProject).assertDocumentChangeIsAllowed(viewProvider);
    super.beforeDocumentChangeOnUnlockedDocument(viewProvider);
  }


  @Override
  protected boolean finishCommitInWriteAction(@NotNull Document document,
                                              @NotNull List<? extends BooleanRunnable> finishProcessors,
                                              @NotNull List<? extends BooleanRunnable> reparseInjectedProcessors,
                                              boolean synchronously) {
    boolean success = super.finishCommitInWriteAction(document, finishProcessors, reparseInjectedProcessors, synchronously);
    PsiFile file = getCachedPsiFile(document);
    if (file != null) {
      InjectedLanguageManagerImpl.clearInvalidInjections(file);
    }
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) { // can be false for non-physical PSI
      InjectedLanguageManagerImpl.disposeInvalidEditors();
    }
    return success;
  }

  @Override
  public boolean isDocumentBlockedByPsi(@NotNull Document document) {
    final List<FileViewProvider> viewProviders = getCachedViewProviders(document);
    if (viewProviders.isEmpty()) return false;

    PostprocessReformattingAspect aspect = PostprocessReformattingAspect.getInstance(myProject);
    for (FileViewProvider viewProvider : viewProviders) {
      if (aspect.isViewProviderLocked(viewProvider)) {
        return true;
      }
    }
    return false;
    // todo IJPL-339 is it correct?
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document document) {
    PostprocessReformattingAspect component = PostprocessReformattingAspect.getInstance(myProject);
    if (component == null) return;

    List<FileViewProvider> viewProviders;
    if (document instanceof DocumentWindow) {
      // todo IJPL-339 implement it
      Document topDoc = ((DocumentWindow)document).getDelegate();
      List<FileViewProvider> topViewProviders = getCachedViewProviders(topDoc);
      if (ContainerUtil.exists(topViewProviders, topViewProvider -> InjectionUtils.shouldFormatOnlyInjectedCode(topViewProvider))) { // todo is it correct?
        viewProviders = getCachedViewProviders(document);
      }
      else {
        viewProviders = topViewProviders;
      }
    }
    else {
      viewProviders = getCachedViewProviders(document);
    }

    // todo IJPL-339 is it correct?
    for (FileViewProvider viewProvider : viewProviders) {
      component.doPostponedFormatting(viewProvider);
    }
  }

  @ApiStatus.Internal
  @NotNull
  @Override
  public List<BooleanRunnable> reparseChangedInjectedFragments(@NotNull Document hostDocument,
                                                               @NotNull PsiFile hostPsiFile,
                                                               @NotNull TextRange hostChangedRange,
                                                               @NotNull ProgressIndicator indicator,
                                                               @NotNull ASTNode oldRoot,
                                                               @NotNull ASTNode newRoot) {
    List<DocumentWindow> changedInjected = InjectedLanguageManager.getInstance(myProject).getCachedInjectedDocumentsInRange(hostPsiFile, hostChangedRange);
    if (changedInjected.isEmpty()) return Collections.emptyList();
    FileViewProvider hostViewProvider = hostPsiFile.getViewProvider();
    List<DocumentWindow> fromLast = new ArrayList<>(changedInjected);
    // make sure modifications do not ruin all document offsets after
    fromLast.sort(Collections.reverseOrder(Comparator.comparingInt(doc -> ArrayUtil.getLastElement(doc.getHostRanges()).getEndOffset())));
    List<BooleanRunnable> result = new ArrayList<>(changedInjected.size());
    for (DocumentWindow document : fromLast) {
      Segment[] ranges = document.getHostRanges();
      if (ranges.length != 0) {
        // host document change has left something valid in this document window place. Try to reparse.
        PsiFile injectedPsiFile = getCachedPsiFile(document);
        if (injectedPsiFile  == null || !injectedPsiFile.isValid()) continue;

        BooleanRunnable runnable = InjectedLanguageUtil.reparse(injectedPsiFile, document, hostPsiFile, hostDocument, hostViewProvider, indicator, oldRoot, newRoot, this);
        ContainerUtil.addIfNotNull(result, runnable);
      }
    }

    return result;
  }

  @Override
  public @NonNls String toString() {
    return super.toString() + " for the project " + myProject + ".";
  }

  @Override
  public void reparseFiles(@NotNull Collection<? extends VirtualFile> files, boolean includeOpenFiles) {
    FileContentUtil.reparseFiles(myProject, files, includeOpenFiles);
  }

  @Override
  protected @NotNull DocumentWindow freezeWindow(@NotNull DocumentWindow document) {
    return InjectedLanguageManager.getInstance(myProject).freezeWindow(document);
  }

  @Override
  public boolean commitAllDocumentsUnderProgress() {
    int eventCount = IdeEventQueue.getInstance().getEventCount();
    boolean success = super.commitAllDocumentsUnderProgress();
    IdeEventQueue.getInstance().setEventCount(eventCount);
    return success;
  }
}

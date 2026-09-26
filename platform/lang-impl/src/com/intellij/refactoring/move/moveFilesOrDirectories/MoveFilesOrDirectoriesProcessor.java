// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Refactoring processor for moving files or sets of files.
 * Uses {@link MoveFileHandler} to run language-specific logic.
 */
public class MoveFilesOrDirectoriesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(MoveFilesOrDirectoriesProcessor.class);

  protected final PsiElement[] myElementsToMove;
  private final boolean mySearchForReferences;
  protected final boolean mySearchInComments;
  protected final boolean mySearchInNonJavaFiles;
  private final @NotNull PsiDirectory myNewParent;
  private final MoveCallback myMoveCallback;
  private NonCodeUsageInfo[] myNonCodeUsages;
  protected final Map<PsiFile, @Unmodifiable List<UsageInfo>> myFoundUsages = new HashMap<>();

  public MoveFilesOrDirectoriesProcessor(@NotNull Project project,
                                         PsiElement @NotNull [] elements,
                                         @NotNull PsiDirectory newParent,
                                         boolean searchInComments,
                                         boolean searchInNonJavaFiles,
                                         MoveCallback moveCallback,
                                         Runnable prepareSuccessfulCallback) {
    this(project, elements, newParent, true, searchInComments, searchInNonJavaFiles, moveCallback, prepareSuccessfulCallback);
  }

  public MoveFilesOrDirectoriesProcessor(@NotNull Project project,
                                         PsiElement @NotNull [] elements,
                                         @NotNull PsiDirectory newParent,
                                         boolean searchForReferences,
                                         boolean searchInComments,
                                         boolean searchInNonJavaFiles,
                                         MoveCallback moveCallback,
                                         Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myElementsToMove = elements;
    myNewParent = newParent;
    mySearchForReferences = searchForReferences;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MoveFilesOrDirectoriesViewDescriptor(myElementsToMove, myNewParent);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    MoveFilesOrDirectoriesUtil.UsagesContext context = MoveFilesOrDirectoriesUtil.findUsages(myProject,
                                                                                             myElementsToMove,
                                                                                             myNewParent,
                                                                                             mySearchForReferences,
                                                                                             mySearchInComments,
                                                                                             mySearchInNonJavaFiles);

    myFoundUsages.putAll(context.classifiedUsages());
    return context.allUsages().toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == myElementsToMove.length);
    System.arraycopy(elements, 0, myElementsToMove, 0, elements.length);
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    if (myNonCodeUsages != null) {
      RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    }
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] _usages) {
    try {
      List<UsageInfo> codeUsages = new ArrayList<>();
      List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<>();
      for (UsageInfo usage : _usages) {
        if (usage instanceof NonCodeUsageInfo) {
          nonCodeUsages.add((NonCodeUsageInfo)usage);
        }
        else {
          codeUsages.add(usage);
        }
      }

      List<RefactoringElementListener> listeners = ContainerUtil.map(myElementsToMove, item -> getTransaction().getElementListener(item));

      MoveFilesOrDirectoriesUtil.MoveElementsResult result = MoveFilesOrDirectoriesUtil.moveElements(
        myProject, myElementsToMove, myNewParent, ProgressManager.getInstance().getProgressIndicator(), mySearchForReferences);

      retargetUsages(codeUsages.toArray(UsageInfo.EMPTY_ARRAY), result.oldToNewMap());
      MoveFilesOrDirectoriesUtil.retargetClassifiedUsages(myFoundUsages, result.oldToNewMap());

      myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[0]);

      MoveFilesOrDirectoriesUtil.afterMovement(listeners, result.movedElementPointers());

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }
      if (MoveFilesOrDirectoriesDialog.isOpenInEditorProperty()) {
        List<PsiFile> justFiles = ContainerUtil.mapNotNull(
          (Collection<? extends SmartPsiElementPointer<PsiFile>>)result.movedFilePointers(), pointer -> pointer.getElement());
        ApplicationManager.getApplication().invokeLater(
          () -> EditorHelper.openFilesInEditor(justFiles.stream().filter(PsiElement::isValid).toArray(PsiFile[]::new)));
      }
    }
    catch (IncorrectOperationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        LOG.info(e);
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(myProject, cause.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon()));
      }
      else {
        LOG.error(e);
      }
    }
  }

  /**
   * @deprecated use {@link MoveFilesOrDirectoriesUtil#doMoveFile} instead
   */
  @Deprecated
  protected void doMoveFile(@NotNull PsiFile movedFile, @NotNull PsiDirectory newParent) {
    MoveFilesOrDirectoriesUtil.doMoveFile(movedFile, newParent);
  }

  /**
   * @deprecated use {@link MoveFilesOrDirectoriesUtil#doMoveDirectory} instead
   */
  @Deprecated
  protected void doMoveDirectory(@NotNull PsiDirectory directory, @NotNull PsiDirectory newParent) {
    MoveFilesOrDirectoriesUtil.doMoveDirectory(directory, newParent);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    UsageInfo[] usages = refUsages.get();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.runBlocking(() -> MoveFileHandler.detectConflicts(myElementsToMove, usages, myNewParent, conflicts)),
      RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }
    return showConflicts(conflicts, usages);
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.move";
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myElementsToMove);
    return data;
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myNewParent);
    return data;
  }

  protected void retargetUsages(UsageInfo @NotNull [] usages, @NotNull Map<PsiElement, PsiElement> oldToNewMap) {
    MoveFilesOrDirectoriesUtil.retargetCodeUsages(usages);
  }

  @Override
  protected @NotNull String getCommandName() {
    return RefactoringBundle.message("move.title");
  }

  @Override
  protected boolean shouldDisableAccessChecks() {
    // No need to check access for files before move
    return true;
  }
}

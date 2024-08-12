// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.EditorHelper;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.FileReferenceContextUtil;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.Reference;
import java.util.*;

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
  private final PsiDirectory myNewParent;
  private final MoveCallback myMoveCallback;
  private NonCodeUsageInfo[] myNonCodeUsages;
  protected final Map<PsiFile, List<UsageInfo>> myFoundUsages = new HashMap<>();

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
    List<UsageInfo> result = new ArrayList<>();
    for (PsiElement element : myElementsToMove) {
      if (mySearchForReferences) {
        for (PsiReference reference : ReferencesSearch.search(element, GlobalSearchScope.projectScope(myProject))) {
          result.add(new MyUsageInfo(reference, element));
        }
      }
      findElementUsages(result, element);
    }

    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private void findElementUsages(@NotNull List<? super UsageInfo> result, @NotNull PsiElement element) {
    if (!mySearchForReferences) {
      return;
    }
    if (element instanceof PsiFile) {
      final List<UsageInfo> usages = MoveFileHandler.forElement((PsiFile)element)
        .findUsages((PsiFile)element, myNewParent, mySearchInComments, mySearchInNonJavaFiles);
      if (usages != null) {
        result.addAll(usages);
        myFoundUsages.put((PsiFile)element, usages);
      }
    }
    else if (element instanceof PsiDirectory) {
      for (PsiElement childElement : element.getChildren()) {
        findElementUsages(result, childElement);
      }
    }
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
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      progressIndicator.setIndeterminate(myElementsToMove.length <= 1); // only show progress when moving multiple elements
      progressIndicator.setFraction(0.0);
      List<PsiElement> toChange = new ArrayList<>();
      Collections.addAll(toChange, myElementsToMove);

      PsiDirectory newParent = myNewParent;

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

      //keep hard references to PSI and AST to prevent collecting the object between saving references and restoring
      Map<PsiFile, FileASTNode> movingFiles = new HashMap<>();

      final Map<PsiElement, PsiElement> oldToNewMap = new HashMap<>();
      if (mySearchForReferences) {
        for (PsiElement element : toChange) {
          if (element instanceof PsiDirectory) {
            encodeDirectoryFiles(element, movingFiles);
          }
          else if (element instanceof PsiFile) {
            movingFiles.put((PsiFile)element, ((PsiFile)element).getNode());
            FileReferenceContextUtil.encodeFileReferences(element);
          }
        }
      }

      List<RefactoringElementListener> listeners = ContainerUtil.map(myElementsToMove, item -> getTransaction().getElementListener(item));

      List<Runnable> notifyListeners = new ArrayList<>();
      List<SmartPsiElementPointer<PsiFile>> movedFiles = new ArrayList<>();
      for (int i = 0; i < myElementsToMove.length; i++) {
        PsiElement element = toChange.get(i);
        progressIndicator.setFraction((double)i / myElementsToMove.length);
        if (element instanceof PsiDirectory directory) {
          progressIndicator.setText2(directory.getVirtualFile().getPresentableUrl());
          MoveFilesOrDirectoriesUtil.doMoveDirectory(directory, newParent);
          for (PsiElement psiElement : directory.getChildren()) {
            processDirectoryFiles(movedFiles, oldToNewMap, psiElement);
          }
        }
        else if (element instanceof PsiFile movedFile) {
          progressIndicator.setText2(movedFile.getVirtualFile().getPresentableUrl());
          MoveFileHandler.forElement(movedFile).prepareMovedFile(movedFile, newParent, oldToNewMap);

          PsiFile moving = newParent.findFile(movedFile.getName());
          if (moving == null) {
            MoveFilesOrDirectoriesUtil.doMoveFile(movedFile, newParent);
          }
          moving = newParent.findFile(movedFile.getName());
          if (moving != null) {
            movedFiles.add(SmartPointerManager.createPointer(moving));
          }
        }

        if (element.isValid()) {
          RefactoringElementListener listener = listeners.get(i);
          SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
          notifyListeners.add(() -> {
            PsiElement restored = pointer.getElement();
            if (restored != null) {
              listener.elementMoved(restored);
            }
          });
        }
      }
      progressIndicator.setText2("");
      progressIndicator.setFraction(1.0);
      // sort by offset descending to process correctly several usages in one PsiElement [IDEADEV-33013]
      UsageInfo[] usages = codeUsages.toArray(UsageInfo.EMPTY_ARRAY);
      CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);

      DumbService.getInstance(myProject).completeJustSubmittedTasks();

      // fix references in moved files to outer files
      for (SmartPsiElementPointer<PsiFile> pointer : movedFiles) {
        PsiFile movedFile = pointer.getElement();
        if (movedFile != null) {
          MoveFileHandler.forElement(movedFile).updateMovedFile(movedFile);
          if (mySearchForReferences) FileReferenceContextUtil.decodeFileReferences(movedFile);
        }
      }

      retargetUsages(usages, oldToNewMap);

      for (Map.Entry<PsiFile, List<UsageInfo>> entry : myFoundUsages.entrySet()) {
        // Before retargeting sort usages by start offset to get consistent results
        ContainerUtil.sort(entry.getValue(), Comparator.comparingInt(o -> {
          PsiElement element = o.getElement();
          if (element == null) return -1;
          return element.getTextRange().getStartOffset();
        }));
        MoveFileHandler.forElement(entry.getKey()).retargetUsages(entry.getValue(), oldToNewMap);
      }

      myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[0]);

      afterMove(movedFiles, notifyListeners);

      Reference.reachabilityFence(movingFiles);
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

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    UsageInfo[] usages = refUsages.get();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.run(() -> MoveFileHandler.detectConflicts(myElementsToMove, usages, myNewParent, conflicts)),
                                                                           RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }
    return showConflicts(conflicts, usages);
  }

  private void afterMove(Collection<? extends SmartPsiElementPointer<PsiFile>> movedFiles, List<? extends Runnable> notifyListeners) {
    notifyListeners.forEach(Runnable::run);
    if (myMoveCallback != null) {
      myMoveCallback.refactoringCompleted();
    }
    if (MoveFilesOrDirectoriesDialog.isOpenInEditorProperty()) {
      List<PsiFile> justFiles = ContainerUtil.mapNotNull(movedFiles, pointer -> pointer.getElement());
      ApplicationManager.getApplication().invokeLater(
        () -> EditorHelper.openFilesInEditor(justFiles.stream().filter(PsiElement::isValid).toArray(PsiFile[]::new)));
    }
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

  private static void encodeDirectoryFiles(@NotNull PsiElement psiElement, @NotNull Map<PsiFile, FileASTNode> movedFiles) {
    if (psiElement instanceof PsiFile) {
      movedFiles.put((PsiFile)psiElement, ((PsiFile)psiElement).getNode());
      FileReferenceContextUtil.encodeFileReferences(psiElement);
    }
    else if (psiElement instanceof PsiDirectory) {
      for (PsiElement element : psiElement.getChildren()) {
        encodeDirectoryFiles(element, movedFiles);
      }
    }
  }

  private static void processDirectoryFiles(@NotNull List<? super SmartPsiElementPointer<PsiFile>> movedFiles, @NotNull Map<PsiElement, PsiElement> oldToNewMap, @NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiFile movedFile) {
      movedFiles.add(SmartPointerManager.createPointer(movedFile));
      MoveFileHandler.forElement(movedFile).prepareMovedFile(movedFile, movedFile.getParent(), oldToNewMap);
    }
    else if (psiElement instanceof PsiDirectory) {
      for (PsiElement element : psiElement.getChildren()) {
        processDirectoryFiles(movedFiles, oldToNewMap, element);
      }
    }
  }

  protected void retargetUsages(UsageInfo @NotNull [] usages, @NotNull Map<PsiElement, PsiElement> oldToNewMap) {
    for (UsageInfo usageInfo : usages) {
      if (usageInfo instanceof MyUsageInfo info) {
        PsiElement element = info.myTarget;

        if (info.getReference() instanceof FileReference || info.getReference() instanceof PsiDynaReference) {
          final PsiElement usageElement = info.getElement();
          if (usageElement != null) {
            final PsiFile usageFile = usageElement.getContainingFile();
            final PsiFile psiFile = usageFile.getViewProvider().getPsi(usageFile.getViewProvider().getBaseLanguage());
            if (psiFile != null && psiFile.equals(element)) {
              continue;  // already processed in MoveFilesOrDirectoriesUtil.doMoveFile
            }
          }
        }
        final PsiElement refElement = info.myReference.getElement();
        if (refElement.isValid()) {
          info.myReference.bindToElement(element);
        }
      }
    }
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

  private static final class MyUsageInfo extends UsageInfo {
    private final PsiElement myTarget;
    final PsiReference myReference;

    MyUsageInfo(@NotNull PsiReference reference, @NotNull PsiElement target) {
      super(reference);
      myReference = reference;
      myTarget = target;
    }
  }
}

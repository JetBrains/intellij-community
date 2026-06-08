// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MoveDirectoryWithClassesProcessor extends BaseRefactoringProcessor {
  private final PsiDirectory[] myDirectories;
  private final PsiDirectory myTargetDirectory;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private final Map<VirtualFile, TargetDirectoryWrapper> myFilesToMove;
  private final Map<PsiDirectory, TargetDirectoryWrapper> myNestedDirsToMove;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private final MoveCallback myMoveCallback;

  public MoveDirectoryWithClassesProcessor(Project project,
                                           PsiDirectory[] directories,
                                           PsiDirectory targetDirectory,
                                           boolean searchInComments,
                                           boolean searchInNonJavaFiles,
                                           boolean includeSelf,
                                           MoveCallback moveCallback) {
    super(project);
    myDirectories = MoveDirectoryWithClassesProcessorUtil.preprocessDirectories(directories, targetDirectory);
    myTargetDirectory = targetDirectory;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
    myFilesToMove = new HashMap<>();
    myNestedDirsToMove = new LinkedHashMap<>();
    for (PsiDirectory dir : myDirectories) {
      MoveDirectoryWithClassesProcessorUtil.collectFiles2Move(myFilesToMove, myNestedDirsToMove, dir,
                                                              includeSelf ? dir.getParentDirectory() : dir, getTargetDirectory(dir));
    }
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MoveMultipleElementsViewDescriptor(
      PsiUtilCore.toPsiFileArray(MoveDirectoryWithClassesProcessorUtil.getPsiFiles(myFilesToMove, myProject)),
      getTargetName());
  }

  protected String getTargetName() {
    return RefactoringUIUtil.getDescription(getTargetDirectory(null).getTargetDirectory(), false);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return MoveDirectoryWithClassesProcessorUtil.findUsages(myProject, myFilesToMove, myDirectories, mySearchInComments, mySearchInNonJavaFiles);
  }

  @Override
  protected boolean preprocessUsages(final @NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(() -> ReadAction.runBlocking(
                                             () -> MoveDirectoryWithClassesProcessorUtil.collectConflicts(conflicts, refUsages, myFilesToMove, myProject, myTargetDirectory)),
                                           RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }
    return showConflicts(conflicts, refUsages.get());
  }

  @Override
  public void performRefactoring(UsageInfo @NotNull [] usages) {
    try {
      MoveDirectoryWithClassesProcessorUtil.doPerformRefactoring(myProject, usages, myDirectories, myNestedDirsToMove, myFilesToMove,
                                                                 getTransaction(), this::getResultDirectory);
      myNonCodeUsages = ContainerUtil.filterIsInstance(usages, NonCodeUsageInfo.class).toArray(NonCodeUsageInfo[]::new);
    }
    catch (IncorrectOperationException e) {
      myNonCodeUsages = new NonCodeUsageInfo[0];
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.move";
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myDirectories);
    return data;
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myTargetDirectory);
    return data;
  }

  private TargetDirectoryWrapper getResultDirectory(PsiDirectory dir) {
    return myTargetDirectory != null
           ? new TargetDirectoryWrapper(myTargetDirectory, dir.getName())
           : getTargetDirectory(dir);
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    if (myNonCodeUsages == null) return; //refactoring was aborted
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    if (myMoveCallback != null) {
      myMoveCallback.refactoringCompleted();
    }
  }

  @Override
  protected @NotNull String getCommandName() {
    return RefactoringBundle.message("moving.directories.command");
  }

  public @NotNull TargetDirectoryWrapper getTargetDirectory(PsiDirectory dir) {
    return new TargetDirectoryWrapper(myTargetDirectory);
  }

  public static final class TargetDirectoryWrapper {
    private TargetDirectoryWrapper myParentDirectory;
    private PsiDirectory myTargetDirectory;
    private String myRelativePath;

    public TargetDirectoryWrapper(PsiDirectory targetDirectory) {
      myTargetDirectory = targetDirectory;
    }

    public TargetDirectoryWrapper(TargetDirectoryWrapper parentDirectory, String relativePath) {
      myParentDirectory = parentDirectory;
      myRelativePath = relativePath;
    }

    public TargetDirectoryWrapper(PsiDirectory parentDirectory, String relativePath) {
      myTargetDirectory = parentDirectory.findSubdirectory(relativePath);
      //in case it was null
      myParentDirectory = new TargetDirectoryWrapper(parentDirectory);
      myRelativePath = relativePath;
    }

    public PsiDirectory findOrCreateTargetDirectory() throws IncorrectOperationException{
      if (myTargetDirectory == null) {
        PsiDirectory root = myParentDirectory.findOrCreateTargetDirectory();

        final String[] pathComponents = myRelativePath.split("/");
        for (String component : pathComponents) {
          myTargetDirectory = root.findSubdirectory(component);
          if (myTargetDirectory == null) {
            myTargetDirectory = root.createSubdirectory(component);
          }
          root = myTargetDirectory;
        }
      }
      return myTargetDirectory;
    }

    public @Nullable PsiDirectory getTargetDirectory() {
      return myTargetDirectory;
    }

    public TargetDirectoryWrapper findOrCreateChild(String relativePath) {
      if (myTargetDirectory != null) {
        final PsiDirectory psiDirectory = myTargetDirectory.findSubdirectory(relativePath);
        if (psiDirectory != null) {
          return new TargetDirectoryWrapper(psiDirectory);
        }
      }
      return new TargetDirectoryWrapper(this, relativePath);
    }

    public void checkMove(PsiFile psiFile) throws IncorrectOperationException {
      if (myTargetDirectory != null) {
        MoveFilesOrDirectoriesUtil.checkMove(psiFile, myTargetDirectory);
      }
    }

    public @NotNull PsiDirectory getRootDirectory() {
      if (myTargetDirectory == null) {
        return myParentDirectory.getRootDirectory();
      }
      return myTargetDirectory;
    }

    public @NotNull String getRelativePathFromRoot() {
      if (myTargetDirectory != null) {
        return "";
      }
      String pathFromRoot = myParentDirectory.getRelativePathFromRoot();
      if (pathFromRoot.isEmpty()) {
        return myRelativePath;
      }
      return myRelativePath + "/" + pathFromRoot;
    }
  }
}

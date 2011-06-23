/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 28-Dec-2009
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.FileReferenceContextUtil;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MoveDirectoryWithClassesProcessor extends BaseRefactoringProcessor {
  private final PsiDirectory[] myDirectories;
  private final PsiDirectory myTargetDirectory;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private final Map<PsiFile, TargetDirectoryWrapper> myFilesToMove;
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
    if (targetDirectory != null) {
      final List<PsiDirectory> dirs = new ArrayList<PsiDirectory>(Arrays.asList(directories));
      for (Iterator<PsiDirectory> iterator = dirs.iterator(); iterator.hasNext(); ) {
        final PsiDirectory directory = iterator.next();
        if (targetDirectory.equals(directory.getParentDirectory()) || targetDirectory.equals(directory)) {
          iterator.remove();
        }
      }
      directories = dirs.toArray(new PsiDirectory[dirs.size()]);
    }
    myDirectories = directories;
    myTargetDirectory = targetDirectory;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
    myFilesToMove = new HashMap<PsiFile, TargetDirectoryWrapper>();
    for (PsiDirectory dir : directories) {
      collectFiles2Move(myFilesToMove, dir, includeSelf ? dir.getParentDirectory() : dir, getTargetDirectory(dir));
    }
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    PsiElement[] elements = new PsiElement[myFilesToMove.size()];
    final PsiFile[] classes = PsiUtilBase.toPsiFileArray(myFilesToMove.keySet());
    System.arraycopy(classes, 0, elements, 0, classes.length);
    return new MoveMultipleElementsViewDescriptor(elements, getTargetName());
  }

  protected String getTargetName() {
    return RefactoringUIUtil.getDescription(getTargetDirectory(null).getTargetDirectory(), false);
  }

  @NotNull
  @Override
  public UsageInfo[] findUsages() {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
      helper.findUsages(myFilesToMove.keySet(), myDirectories, usages, mySearchInComments, mySearchInNonJavaFiles, myProject);
    }
    return UsageViewUtil.removeDuplicatedUsages(usages.toArray(new UsageInfo[usages.size()]));
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    for (PsiFile psiFile : myFilesToMove.keySet()) {
      try {
        myFilesToMove.get(psiFile).checkMove(psiFile);
      }
      catch (IncorrectOperationException e) {
        conflicts.putValue(psiFile, e.getMessage());
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {}

  @Override
  public void performRefactoring(UsageInfo[] usages) {
    //try to create all directories beforehand
    try {
      //top level directories should be created even if they are empty
      for (PsiDirectory directory : myDirectories) {
        final TargetDirectoryWrapper targetSubDirectory =
          myTargetDirectory != null
          ? new TargetDirectoryWrapper(myTargetDirectory, directory.getName())
          : getTargetDirectory(directory);
        targetSubDirectory.findOrCreateTargetDirectory();
      }
      for (PsiFile psiFile : myFilesToMove.keySet()) {
        myFilesToMove.get(psiFile).findOrCreateTargetDirectory();
      }
    }
    catch (IncorrectOperationException e) {
      Messages.showErrorDialog(myProject, e.getMessage(), CommonBundle.getErrorTitle());
      return;
    }
    final List<PsiFile> movedFiles = new ArrayList<PsiFile>();
    final Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<PsiElement, PsiElement>();
    for (PsiFile psiFile : myFilesToMove.keySet()) {
      for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
        helper.beforeMove(psiFile);
      }
      final RefactoringElementListener listener = getTransaction().getElementListener(psiFile);
      final PsiDirectory moveDestination = myFilesToMove.get(psiFile).getTargetDirectory();

      for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
        boolean processed = helper.move(psiFile, moveDestination, oldToNewElementsMapping, movedFiles, listener);
        if (processed) {
          break;
        }
      }
    }
    for (PsiElement newElement : oldToNewElementsMapping.values()) {
      for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
        helper.afterMove(newElement);
      }
    }

    // fix references in moved files to outer files
    for (PsiFile movedFile : movedFiles) {
      MoveFileHandler.forElement(movedFile).updateMovedFile(movedFile);
      FileReferenceContextUtil.decodeFileReferences(movedFile);
    }

    myNonCodeUsages = CommonMoveUtil.retargetUsages(usages, oldToNewElementsMapping);
    for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
      helper.postProcessUsages(usages);
    }
    for (PsiDirectory directory : myDirectories) {
      directory.delete();
    }
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    if (myMoveCallback != null) {
      myMoveCallback.refactoringCompleted();
    }
  }

  private static void collectFiles2Move(Map<PsiFile, TargetDirectoryWrapper> files2Move,
                                     PsiDirectory directory,
                                     PsiDirectory rootDirectory,
                                     @NotNull TargetDirectoryWrapper targetDirectory) {
    final PsiElement[] children = directory.getChildren();
    final String relativePath = VfsUtil.getRelativePath(directory.getVirtualFile(), rootDirectory.getVirtualFile(), '/');

    final TargetDirectoryWrapper newTargetDirectory = relativePath.length() == 0
                                                      ? targetDirectory
                                                      : targetDirectory.findOrCreateChild(relativePath);
    for (PsiElement child : children) {
      if (child instanceof PsiFile) {
        files2Move.put((PsiFile)child, newTargetDirectory);
      }
      else if (child instanceof PsiDirectory){
        collectFiles2Move(files2Move, (PsiDirectory)child, directory, newTargetDirectory);
      }
    }
  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("moving.directories.command");
  }

  public TargetDirectoryWrapper getTargetDirectory(PsiDirectory dir) {
    return new TargetDirectoryWrapper(myTargetDirectory);
  }

  public static class TargetDirectoryWrapper {
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
        final PsiDirectory root = myParentDirectory.findOrCreateTargetDirectory();

        myTargetDirectory = root.findSubdirectory(myRelativePath);
        if (myTargetDirectory == null) {
          myTargetDirectory = root.createSubdirectory(myRelativePath);
        }
      }
      return myTargetDirectory;
    }

    @Nullable
    public PsiDirectory getTargetDirectory() {
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
        psiFile.getManager().checkMove(psiFile, myTargetDirectory);
      }
    }
  }
}

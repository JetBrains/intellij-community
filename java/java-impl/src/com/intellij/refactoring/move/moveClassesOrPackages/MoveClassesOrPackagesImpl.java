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

/**
 * created at Nov 27, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.DirectoryChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MoveClassesOrPackagesImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl");

  public static void doMove(final Project project,
                            PsiElement[] elements,
                            PsiElement initialTargetElement,
                            final MoveCallback moveCallback) {
    final PsiElement[] psiElements = adjustForMove(project, elements, initialTargetElement);
    if (psiElements == null) {
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(psiElements), true)) {
      return;
    }

    final String initialTargetPackageName = getInitialTargetPackageName(initialTargetElement, psiElements);
    final PsiDirectory initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, psiElements);
    final boolean isTargetDirectoryFixed = initialTargetDirectory == null;

    boolean searchTextOccurences = false;
    for (int i = 0; i < psiElements.length && !searchTextOccurences; i++) {
      PsiElement psiElement = psiElements[i];
      searchTextOccurences = TextOccurrencesUtil.isSearchTextOccurencesEnabled(psiElement);
    }
    final MoveClassesOrPackagesDialog moveDialog =
      new MoveClassesOrPackagesDialog(project, searchTextOccurences, psiElements, initialTargetElement, moveCallback);
    boolean searchInComments = JavaRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
    boolean searchForTextOccurences = JavaRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT;
    moveDialog.setData(psiElements, initialTargetPackageName, initialTargetDirectory, isTargetDirectoryFixed, searchInComments,
                       searchForTextOccurences, HelpID.getMoveHelpID(psiElements[0]));
    moveDialog.show();
  }

  @Nullable
  public static PsiElement[] adjustForMove(final Project project, final PsiElement[] elements, final PsiElement targetElement) {
    final PsiElement[] psiElements = new PsiElement[elements.length];
    List<String> names = new ArrayList<String>();
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (element instanceof PsiDirectory) {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
        LOG.assertTrue(aPackage != null);
        if (aPackage.getQualifiedName().length() == 0) { //is default package
          String message = RefactoringBundle.message("move.package.refactoring.cannot.be.applied.to.default.package");
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }
        if (!checkNesting(project, aPackage, targetElement)) return null;
        if (!checkMovePackage(project, aPackage)) return null;
        element = aPackage;
      }
      else if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)element;
        if (!checkNesting(project, psiPackage, targetElement)) return null;
        if (!checkMovePackage(project, psiPackage)) return null;
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass instanceof PsiAnonymousClass) {
          String message = RefactoringBundle.message("move.class.refactoring.cannot.be.applied.to.anonymous.classes");
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }
        if (!(aClass.getParent() instanceof PsiFile)) {
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("moving.local.classes.is.not.supported"));
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }

        String name = null;
        for (MoveClassHandler nameProvider : MoveClassHandler.EP_NAME.getExtensions()) {
          name = nameProvider.getName(aClass);
          if (name != null) break;
        }
        if (name == null) name = aClass.getContainingFile().getName();

        if (names.contains(name)) {
          String message = RefactoringBundle
            .getCannotRefactorMessage(RefactoringBundle.message("there.are.going.to.be.multiple.destination.files.with.the.same.name"));
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }

        names.add(name);
      }
      psiElements[idx] = element;
    }

    return psiElements;
  }

  private static boolean checkMovePackage(Project project, PsiPackage aPackage) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
    if (directories.length > 1 || virtualFiles.length > 0) {
      final StringBuffer message = new StringBuffer();
      RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, aPackage.getQualifiedName());
      if (directories.length > 1) {
        DirectoryAsPackageRenameHandler.buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
        message.append("\n\n");
        String report = RefactoringBundle
          .message("all.these.directories.will.be.moved.and.all.references.to.0.will.be.changed", aPackage.getQualifiedName());
        message.append(report);
      }
      message.append("\n");
      message.append(RefactoringBundle.message("do.you.wish.to.continue"));
      int ret =
        Messages.showYesNoDialog(project, message.toString(), RefactoringBundle.message("warning.title"), Messages.getWarningIcon());
      if (ret != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean checkNesting(final Project project, final PsiPackage srcPackage, final PsiElement targetElement) {
    final PsiPackage targetPackage = targetElement instanceof PsiPackage
                                     ? (PsiPackage)targetElement
                                     : targetElement instanceof PsiDirectory ? JavaDirectoryService.getInstance()
                                       .getPackage((PsiDirectory)targetElement) : null;
    for (PsiPackage curPackage = targetPackage; curPackage != null; curPackage = curPackage.getParentPackage()) {
      if (curPackage.equals(srcPackage)) {
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"),
                                               RefactoringBundle.message("cannot.move.package.into.itself"),
                                               HelpID.getMoveHelpID(srcPackage), project);
        return false;
      }
    }
    return true;
  }

  public static String getInitialTargetPackageName(PsiElement initialTargetElement, final PsiElement[] movedElements) {
    String name = getContainerPackageName(initialTargetElement);
    if (name == null) {
      if (movedElements != null) {
        name = getTargetPackageNameForMovedElement(movedElements[0]);
      }
      if (name == null) {
        final PsiDirectory commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null && JavaDirectoryService.getInstance().getPackage(commonDirectory) != null) {
          name = JavaDirectoryService.getInstance().getPackage(commonDirectory).getQualifiedName();
        }
      }
    }
    if (name == null) {
      name = "";
    }
    return name;
  }

  @Nullable
  private static PsiDirectory getCommonDirectory(PsiElement[] movedElements) {
    PsiDirectory commonDirectory = null;

    for (PsiElement movedElement : movedElements) {
      final PsiFile containingFile = movedElement.getContainingFile();
      if (containingFile != null) {
        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        if (containingDirectory != null) {
          if (commonDirectory == null) {
            commonDirectory = containingDirectory;
          }
          else {
            if (commonDirectory != containingDirectory) {
              return null;
            }
          }
        }
      }
    }
    if (commonDirectory != null) {
      return commonDirectory;
    }
    else {
      return null;
    }
  }

  private static String getContainerPackageName(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      return ((PsiPackage)psiElement).getQualifiedName();
    }
    else if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)psiElement);
      return aPackage != null ? aPackage.getQualifiedName() : "";
    }
    else if (psiElement != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiElement.getContainingFile().getContainingDirectory());
      return aPackage != null ? aPackage.getQualifiedName() : "";
    }
    else {
      return null;
    }
  }

  private static String getTargetPackageNameForMovedElement(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      final PsiPackage psiPackage = (PsiPackage)psiElement;
      final PsiPackage parentPackage = psiPackage.getParentPackage();
      return parentPackage != null ? parentPackage.getQualifiedName() : "";
    }
    else if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)psiElement);
      return aPackage != null ? getTargetPackageNameForMovedElement(aPackage) : "";
    }
    else if (psiElement != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiElement.getContainingFile().getContainingDirectory());
      return aPackage != null ? aPackage.getQualifiedName() : "";
    }
    else {
      return null;
    }
  }


  public static PsiDirectory getInitialTargetDirectory(PsiElement initialTargetElement, final PsiElement[] movedElements) {
    PsiDirectory initialTargetDirectory = getContainerDirectory(initialTargetElement);
    if (initialTargetDirectory == null) {
      if (movedElements != null) {
        final PsiDirectory commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null) {
          initialTargetDirectory = commonDirectory;
        }
        else {
          initialTargetDirectory = getContainerDirectory(movedElements[0]);
        }
      }
    }
    return initialTargetDirectory;
  }

  @Nullable
  public static PsiDirectory getContainerDirectory(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)psiElement).getDirectories();
      return directories.length == 1 ? directories[0] : null; //??
    }
    if (psiElement instanceof PsiDirectory) {
      return (PsiDirectory)psiElement;
    }
    if (psiElement != null) {
      return psiElement.getContainingFile().getContainingDirectory();
    }
    return null;
  }

  public static void doRearrangePackage(final Project project, final PsiDirectory[] directories) {
    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(directories), true)) {
      return;
    }

    List<PsiDirectory> sourceRootDirectories = buildRearrangeTargetsList(project, directories);
    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle(RefactoringBundle.message("select.source.root.chooser.title"));
    chooser.fillList(sourceRootDirectories.toArray(new PsiDirectory[sourceRootDirectories.size()]), null, project, "");
    chooser.show();
    if (!chooser.isOK()) return;
    final PsiDirectory selectedTarget = chooser.getSelectedDirectory();
    if (selectedTarget == null) return;
    final Ref<IncorrectOperationException> ex = Ref.create(null);
    final String commandDescription = RefactoringBundle.message("moving.directories.command");
    Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            LocalHistoryAction a = LocalHistory.getInstance().startAction(commandDescription);
            try {
              rearrangeDirectoriesToTarget(directories, selectedTarget);
            }
            catch (IncorrectOperationException e) {
              ex.set(e);
            }
            finally {
              a.finish();
            }
          }
        });
      }
    };
    CommandProcessor.getInstance().executeCommand(project, runnable, commandDescription, null);
    if (ex.get() != null) {
      RefactoringUIUtil.processIncorrectOperation(project, ex.get());
    }
  }

  private static List<PsiDirectory> buildRearrangeTargetsList(final Project project, final PsiDirectory[] directories) {
    final VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
    List<PsiDirectory> sourceRootDirectories = new ArrayList<PsiDirectory>();
    sourceRoots:
    for (final VirtualFile sourceRoot : sourceRoots) {
      PsiDirectory sourceRootDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      if (sourceRootDirectory == null) continue;
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(sourceRootDirectory);
      if (aPackage == null) continue;
      final String packagePrefix = aPackage.getQualifiedName();
      for (final PsiDirectory directory : directories) {
        String qualifiedName = JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName();
        if (!qualifiedName.startsWith(packagePrefix)) {
          continue sourceRoots;
        }
      }
      sourceRootDirectories.add(sourceRootDirectory);
    }
    return sourceRootDirectories;
  }

  private static void rearrangeDirectoriesToTarget(PsiDirectory[] directories, PsiDirectory selectedTarget)
    throws IncorrectOperationException {
    final VirtualFile sourceRoot = selectedTarget.getVirtualFile();
    for (PsiDirectory directory : directories) {
      final PsiPackage parentPackage = JavaDirectoryService.getInstance().getPackage(directory).getParentPackage();
      final PackageWrapper wrapper = new PackageWrapper(parentPackage);
      final PsiDirectory moveTarget = RefactoringUtil.createPackageDirectoryInSourceRoot(wrapper, sourceRoot);
      MoveClassesOrPackagesUtil.moveDirectoryRecursively(directory, moveTarget);
    }
  }

}

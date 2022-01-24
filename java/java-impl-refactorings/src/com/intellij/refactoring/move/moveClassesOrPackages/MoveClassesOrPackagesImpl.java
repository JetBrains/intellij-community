// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.DirectoryChooser;
import com.intellij.ide.util.PlatformPackageUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandlerBase;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

public final class MoveClassesOrPackagesImpl {
  private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesImpl.class);

  public static void doMove(Project project, PsiElement[] adjustedElements, PsiElement initialTargetElement, MoveCallback moveCallback) {
    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements), true)) {
      return;
    }

    String initialTargetPackageName = getInitialTargetPackageName(initialTargetElement, adjustedElements);
    PsiDirectory initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, adjustedElements);
    boolean searchTextOccurrences = Stream.of(adjustedElements).anyMatch(TextOccurrencesUtil::isSearchTextOccurrencesEnabled);
    boolean searchInComments = JavaRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
    boolean searchForTextOccurrences = JavaRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT;
    new MoveClassesOrPackagesDialog(
      project, searchTextOccurrences, adjustedElements, initialTargetElement, moveCallback, initialTargetPackageName,
      initialTargetDirectory, searchInComments, searchForTextOccurrences
    ).show();
  }

  public static PsiElement @Nullable [] adjustForMove(final Project project, final PsiElement[] elements, final PsiElement targetElement) {
    final PsiElement[] psiElements = new PsiElement[elements.length];
    List<String> names = new ArrayList<>();
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (element instanceof PsiDirectory) {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
        LOG.assertTrue(aPackage != null);
        if (aPackage.getQualifiedName().isEmpty()) { //is default package
          String message = JavaRefactoringBundle.message("move.package.refactoring.cannot.be.applied.to.default.package");
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }
        if (!checkNesting(project, aPackage, targetElement, true)) return null;
        if (!isAlreadyChecked(psiElements, idx, aPackage) && !checkMovePackage(project, aPackage, (PsiDirectory)element)) return null;
        element = aPackage;
      }
      else if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)element;
        if (!checkNesting(project, psiPackage, targetElement, true)) return null;
        if (!checkMovePackage(project, psiPackage, null)) return null;
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass instanceof PsiAnonymousClass) {
          String message = JavaRefactoringBundle.message("move.class.refactoring.cannot.be.applied.to.anonymous.classes");
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }
        if (isClassInnerOrLocal(aClass)) {
          String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("moving.local.classes.is.not.supported"));
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
            .getCannotRefactorMessage(JavaRefactoringBundle.message("there.are.going.to.be.multiple.destination.files.with.the.same.name"));
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }

        names.add(name);
      }
      psiElements[idx] = element;
    }

    return psiElements;
  }

  static boolean isClassInnerOrLocal(PsiClass aClass) {
    return aClass.getContainingClass() != null || aClass.getQualifiedName() == null;
  }

  private static boolean isAlreadyChecked(PsiElement[] psiElements, int idx, PsiPackage aPackage) {
    for (int i = 0; i < idx; i++) {
      if (Comparing.equal(psiElements[i], aPackage)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkMovePackage(Project project, PsiPackage aPackage, @Nullable PsiDirectory currentDirectory) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
    if (directories.length > 1 || virtualFiles.length > 0) {
      final @Nls StringBuffer message = new StringBuffer();
      RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, aPackage.getQualifiedName());
      if (directories.length > 1) {
        DirectoryAsPackageRenameHandlerBase.buildMultipleDirectoriesInPackageMessage(message, aPackage.getQualifiedName(), directories, currentDirectory);
        message.append("\n\n");
        String report = JavaRefactoringBundle
          .message("all.these.directories.will.be.moved.and.all.references.to.0.will.be.changed", aPackage.getQualifiedName());
        message.append(report);
      }
      message.append("\n");
      message.append(RefactoringBundle.message("do.you.wish.to.continue"));
      String resultMessage = message.toString();
      int ret = Messages.showYesNoDialog(project, resultMessage, RefactoringBundle.message("warning.title"), Messages.getQuestionIcon());
      if (ret != Messages.YES) {
        return false;
      }
    }
    return true;
  }

  static boolean checkNesting(final Project project, final PsiPackage srcPackage, final PsiElement targetElement, boolean showError) {
    final PsiPackage targetPackage = targetElement instanceof PsiPackage
                                     ? (PsiPackage)targetElement
                                     : targetElement instanceof PsiDirectory ? JavaDirectoryService.getInstance()
                                       .getPackage((PsiDirectory)targetElement) : null;
    for (PsiPackage curPackage = targetPackage; curPackage != null; curPackage = curPackage.getParentPackage()) {
      if (curPackage.equals(srcPackage)) {
        if (showError) {
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"),
                                                 JavaRefactoringBundle.message("cannot.move.package.into.itself"),
                                                 HelpID.getMoveHelpID(srcPackage), project);
        }
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
      PsiDirectory directory = PlatformPackageUtil.getDirectory(psiElement);
      PsiPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
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

    Map<PsiDirectory, String> sourceRootDirectories = buildRearrangeTargetsList(project, directories);
    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle(JavaRefactoringBundle.message("dialog.title.move.directory.to.source.root"));
    chooser.setDescription(JavaRefactoringBundle.message("move.label.text") + "  ../" + SymbolPresentationUtil.getFilePathPresentation(directories[0]));
    chooser.fillList(sourceRootDirectories.keySet().toArray(PsiDirectory.EMPTY_ARRAY), null, project, sourceRootDirectories);
    if (!chooser.showAndGet()) {
      return;
    }
    final PsiDirectory selectedTarget = chooser.getSelectedDirectory();
    if (selectedTarget == null) return;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final Runnable analyzeConflicts = () -> ApplicationManager.getApplication().runReadAction(() -> RefactoringConflictsUtil
      .analyzeModuleConflicts(project, Arrays.asList(directories), UsageInfo.EMPTY_ARRAY, selectedTarget, conflicts));
    if (!ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(analyzeConflicts, JavaRefactoringBundle.message("analyze.module.conflicts"), true, project)) {
      return;
    }
    if (!BaseRefactoringProcessor.processConflicts(project, conflicts)) return;
    final Ref<IncorrectOperationException> ex = Ref.create(null);
    final String commandDescription = RefactoringBundle.message("moving.directories.command");
    Runnable runnable = () -> ApplicationManager.getApplication().runWriteAction(() -> {
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
    });
    CommandProcessor.getInstance().executeCommand(project, runnable, commandDescription, null);
    if (ex.get() != null) {
      RefactoringUIUtil.processIncorrectOperation(project, ex.get());
    }
  }

  private static LinkedHashMap<PsiDirectory, String> buildRearrangeTargetsList(final Project project, final PsiDirectory[] directories) {
    final List<VirtualFile> sourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(project);
    LinkedHashMap<PsiDirectory, String> sourceRootDirectories = new LinkedHashMap<>();
    sourceRoots:
    for (final VirtualFile sourceRoot : sourceRoots) {
      PsiDirectory sourceRootDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      if (sourceRootDirectory == null) continue;
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(sourceRootDirectory);
      if (aPackage == null) continue;
      final String packagePrefix = aPackage.getQualifiedName();
      String qualifiedName = null;
      for (final PsiDirectory directory : directories) {
        qualifiedName = JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName();
        if (!qualifiedName.startsWith(packagePrefix)) {
          continue sourceRoots;
        }
      }
      sourceRootDirectories.put(sourceRootDirectory, qualifiedName != null ? File.separator + qualifiedName.replace('.', File.separatorChar) : null);
    }
    return sourceRootDirectories;
  }

  private static void rearrangeDirectoriesToTarget(PsiDirectory[] directories, PsiDirectory selectedTarget)
    throws IncorrectOperationException {
    final VirtualFile sourceRoot = selectedTarget.getVirtualFile();
    for (PsiDirectory directory : directories) {
      final PsiPackage parentPackage = JavaDirectoryService.getInstance().getPackage(directory).getParentPackage();
      final PackageWrapper wrapper = new PackageWrapper(parentPackage);
      final PsiDirectory moveTarget = CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(wrapper, sourceRoot);
      MoveClassesOrPackagesUtil.moveDirectoryRecursively(directory, moveTarget);
    }
  }
}
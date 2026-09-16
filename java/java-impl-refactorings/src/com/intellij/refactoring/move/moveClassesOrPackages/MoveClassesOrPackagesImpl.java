// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.DirectoryChooser;
import com.intellij.ide.util.PlatformPackageUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandlerBase;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /**
   * Version of {@link MoveClassesOrPackagesImpl#getTargetsForMove(PsiElement[], PsiElement)} that can show UI.
   * @see MoveClassesOrPackagesImpl#getTargetsForMove(PsiElement[], PsiElement)
   */
  public static PsiElement @Nullable [] adjustForMove(final Project project, final PsiElement[] elements, final PsiElement targetElement) {
    return switch (getTargetsForMove(elements, targetElement)) {
      case ElementsOrError.Error error -> {
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.title"), error.message(), error.helpId(), project);
        yield null;
      }
      case ElementsOrError.Elements adjusted -> {
        if (!canMoveAllDirectoriesForPackages(project, elements)) yield null;
        yield adjusted.elements();
      }
    };
  }

  /**
   * Adjusts move candidates in a way it will be possible to move them by {@link MoveClassesOrPackagesProcessor}.
   * @param elements move candidates to adjust.
   * @param targetElement destination directory or package.
   * @return array of adjusted elements or error that can be displayed to the user.
   */
  public static @NotNull ElementsOrError getTargetsForMove(final PsiElement @NotNull [] elements,
                                                           final @Nullable PsiElement targetElement) {
    final PsiElement[] psiElements = new PsiElement[elements.length];
    List<String> names = new ArrayList<>();
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (element instanceof PsiDirectory directory) {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        LOG.assertTrue(aPackage != null);
        if (aPackage.getQualifiedName().isEmpty()) { //is default package
          return new ElementsOrError.Error(
            JavaRefactoringBundle.message("move.package.refactoring.cannot.be.applied.to.default.package"),
            HelpID.getMoveHelpID(directory));
        }
        if (!checkNesting(aPackage, targetElement)) {
          return new ElementsOrError.Error(JavaRefactoringBundle.message("cannot.move.package.into.itself"),
                                           HelpID.getMoveHelpID(aPackage));
        }
        element = aPackage;
      }
      else if (element instanceof PsiPackage psiPackage) {
        if (!checkNesting(psiPackage, targetElement)) {
          return new ElementsOrError.Error(JavaRefactoringBundle.message("cannot.move.package.into.itself"),
                                           HelpID.getMoveHelpID(psiPackage));
        }
      }
      else if (element instanceof PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass) {
          return new ElementsOrError.Error(
            JavaRefactoringBundle.message("move.class.refactoring.cannot.be.applied.to.anonymous.classes"),
            HelpID.getMoveHelpID(element));
        }
        if (isClassInnerOrLocal(aClass)) {
          return new ElementsOrError.Error(
            RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("moving.local.classes.is.not.supported")),
            HelpID.getMoveHelpID(element));
        }

        String name = null;
        for (MoveClassHandler nameProvider : MoveClassHandler.EP_NAME.getExtensions()) {
          name = nameProvider.getName(aClass);
          if (name != null) break;
        }
        if (name == null) name = aClass.getContainingFile().getName();

        if (names.contains(name)) {
          return new ElementsOrError.Error(
            RefactoringBundle.getCannotRefactorMessage(
              JavaRefactoringBundle.message("there.are.going.to.be.multiple.destination.files.with.the.same.name")),
            HelpID.getMoveHelpID(element));
        }

        names.add(name);
      }
      psiElements[idx] = element;
    }

    return new ElementsOrError.Elements(psiElements);
  }

  static boolean isClassInnerOrLocal(PsiClass aClass) {
    return aClass.getContainingClass() != null || aClass.getQualifiedName() == null;
  }

  /**
   * Checks whether any package corresponds to multiple directories and possibly asks the user whether to move all directories.
   * When is {@code shouldAsk} is false, assumes that such move is not possible.
   */
  private static boolean canMoveAllDirectoriesForPackages(@NotNull Project project, PsiElement @NotNull [] elements) {
    Set<PsiPackage> checkedPackages = new HashSet<>();
    for (PsiElement element : elements) {
      PsiPackage aPackage = null;
      PsiDirectory currentDirectory = null;
      if (element instanceof PsiDirectory directory) {
        aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        currentDirectory = directory;
      }
      else if (element instanceof PsiPackage psiPackage) {
        aPackage = psiPackage;
      }
      if (aPackage == null || !checkedPackages.add(aPackage)) continue;
      String warning = getMoveAllDirectoriesForPackageWarning(aPackage, currentDirectory);
      if (warning != null && !isAgreeToMoveAllDirectoriesForPackage(project, warning)) return false;
    }
    return true;
  }

  private static boolean isAgreeToMoveAllDirectoriesForPackage(@NotNull Project project,
                                                               @NlsContexts.DialogMessage @NotNull String warning) {
    var message = warning + "\n" + RefactoringBundle.message("do.you.wish.to.continue");
    var ret = Messages.showYesNoDialog(project, message, RefactoringBundle.message("warning.title"), Messages.getQuestionIcon());
    return ret == Messages.YES;
  }

  private static @Nullable @NlsContexts.DialogMessage String getMoveAllDirectoriesForPackageWarning(PsiPackage aPackage, @Nullable PsiDirectory currentDirectory) {
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
      return message.toString();
    }
    return null;
  }

  static boolean checkNesting(final PsiPackage srcPackage, final PsiElement targetElement) {
    final PsiPackage targetPackage = targetElement instanceof PsiPackage
                                     ? (PsiPackage)targetElement
                                     : targetElement instanceof PsiDirectory ? JavaDirectoryService.getInstance()
                                       .getPackage((PsiDirectory)targetElement) : null;
    for (PsiPackage curPackage = targetPackage; curPackage != null; curPackage = curPackage.getParentPackage()) {
      if (curPackage.equals(srcPackage)) {
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

  private static @Nullable PsiDirectory getCommonDirectory(PsiElement[] movedElements) {
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
    return switch (psiElement) {
      case null -> null;
      case PsiPackage psiPackage -> psiPackage.getQualifiedName();
      case PsiDirectory directory -> {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        yield aPackage != null ? aPackage.getQualifiedName() : "";
      }
      default -> {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiElement.getContainingFile().getContainingDirectory());
        yield aPackage != null ? aPackage.getQualifiedName() : "";
      }
    };
  }

  private static String getTargetPackageNameForMovedElement(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage psiPackage) {
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

  public static @Nullable PsiDirectory getContainerDirectory(final PsiElement psiElement) {
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
    final Runnable analyzeConflicts = () -> ReadAction.runBlocking(() -> {
      final Collection<? extends PsiElement> scopes = Arrays.asList(directories);
      final VirtualFile vFile = PsiUtilCore.getVirtualFile(selectedTarget);
      if (vFile != null) {
        RefactoringConflictsUtil.getInstance().analyzeModuleConflicts(project, scopes, UsageInfo.EMPTY_ARRAY, vFile, conflicts);
      }
    });
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

  /**
   * Represents the result of adjusting move candidates for move.
   */
  public sealed interface ElementsOrError
    permits ElementsOrError.Elements, ElementsOrError.Error {

    /**
     * Success result of adjusting move candidates.
     * @param elements array of adjusted elements.
     */
    record Elements(PsiElement @NotNull [] elements) implements ElementsOrError {
    }

    /**
     * Error that happened during adjustment of move candidates.
     * @param message error message that can be displayed to the user
     * @param helpId help id that can be used to show help to the user
     */
    record Error(@NlsContexts.DialogMessage @NotNull String message, @NonNls @Nullable String helpId) implements ElementsOrError {
    }
  }
}
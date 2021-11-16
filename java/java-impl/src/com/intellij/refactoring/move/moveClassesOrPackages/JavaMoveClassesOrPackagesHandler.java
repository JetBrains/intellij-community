// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.CommonBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PackageUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class JavaMoveClassesOrPackagesHandler extends MoveHandlerDelegate {

  public static boolean isPackageOrDirectory(final PsiElement element) {
    if (element instanceof PsiPackage) return true;
    return element instanceof PsiDirectory && JavaDirectoryService.getInstance().getPackageInSources((PsiDirectory)element) != null;
  }

  public static boolean isReferenceInAnonymousClass(@Nullable final PsiReference reference) {
    if (reference instanceof PsiJavaCodeReferenceElement &&
       ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnonymousClass) {
      return true;
    }
    return false;
  }

  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer, @Nullable PsiReference reference) {
    for (PsiElement element : elements) {
      if (!isPackageOrDirectory(element) && invalid4Move(element)) return false;
    }
    if (isReferenceInAnonymousClass(reference)) return false;
    return targetContainer == null || super.canMove(elements, targetContainer, reference);
  }

  @Nullable
  @Override
  public String getActionName(PsiElement @NotNull [] elements) {
    int classCount = 0, directoryCount = 0;
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) classCount++;
      else if (element instanceof PsiDirectory || element instanceof PsiPackage) directoryCount++;
    }
    if (directoryCount == 0) {
      return classCount == 1 ? JavaRefactoringBundle.message("move.class") : JavaRefactoringBundle.message("move.classes");
    }
    if (classCount == 0) {
      return directoryCount == 1 ? JavaRefactoringBundle.message("move.package.or.directory")
                                 : JavaRefactoringBundle.message("move.packages.or.directories");
    }
    return JavaRefactoringBundle.message("move.classes.and.packages");
  }

  public static boolean invalid4Move(PsiElement element) {
    PsiFile parentFile;
    if (element instanceof PsiClassOwner) {
      final PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length == 0 && !PackageUtil.isPackageInfoFile(element)) return true;
      for (PsiClass aClass : classes) {
        if (aClass instanceof PsiSyntheticClass) return true;
      }
      parentFile = (PsiFile)element;
    }
    else {
      if (element instanceof PsiSyntheticClass) return true;
      if (!(element instanceof PsiClass)) return true;
      if (element instanceof PsiAnonymousClass) return true;
      if (((PsiClass)element).getContainingClass() != null) return true;
      parentFile = element.getContainingFile();
    }
    return parentFile instanceof PsiJavaFile && JavaProjectRootsUtil.isOutsideJavaSourceRoot(parentFile);
  }

  @Override
  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    if (isPackageOrDirectory(psiElement)) return true;
    boolean areAllClasses = true;
    for (PsiElement source : sources) {
      areAllClasses &= !isPackageOrDirectory(source) && !invalid4Move(source);
    }
    return areAllClasses && psiElement instanceof PsiClass;
  }

  @Override
  public PsiElement[] adjustForMove(final Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
    return MoveClassesOrPackagesImpl.adjustForMove(project,sourceElements, targetElement);
  }

  @Override
  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (canMoveOrRearrangePackages(elements) ) {
      final PsiDirectory[] directories = new PsiDirectory[elements.length];
      System.arraycopy(elements, 0, directories, 0, directories.length);
      if (directories.length > 1 || targetContainer != null) {
        moveAsDirectory(project, targetContainer, callback, directories);
        return;
      }

      int ret = Messages.showYesNoCancelDialog(project,
                                               JavaBundle.message("where.do.you.want.to.move.directory.prompt", "../" + SymbolPresentationUtil.getFilePathPresentation(directories[0])),
                                               JavaBundle.message("dialog.title.move.directory"), JavaBundle.message("button.to.another.directory"), JavaBundle.message("button.to.another.source.root"), CommonBundle.getCancelButtonText(),
                                               Messages.getQuestionIcon());
      if (ret == Messages.YES) {
        moveAsDirectory(project, null, callback, directories);
      }
      else if (ret == Messages.NO) {
        MoveClassesOrPackagesImpl.doRearrangePackage(project, directories);
      }
      return;
    }

    moveAsPackage(project, elements, targetContainer, callback);
  }

  private void moveAsPackage(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    final PsiElement[] adjustedElements = MoveClassesOrPackagesImpl.adjustForMove(project, elements, targetContainer);
    if (adjustedElements == null) return;

    if (targetContainer instanceof PsiDirectory) {
      if (CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements), true)) {
        if (!packageHasMultipleDirectoriesInModule(project, (PsiDirectory)targetContainer)) {
          createMoveClassesOrPackagesToNewDirectoryDialog((PsiDirectory)targetContainer, adjustedElements, callback).show();
          return;
        }
      }
    }
    doMoveWithMoveClassesDialog(project, adjustedElements, targetContainer, callback);
  }

  protected void doMoveWithMoveClassesDialog(final Project project,
                                             PsiElement[] adjustedElements,
                                             PsiElement initialTargetElement,
                                             final MoveCallback moveCallback) {
    MoveClassesOrPackagesImpl.doMove(project, adjustedElements, initialTargetElement, moveCallback);
  }

  @NotNull
  protected DialogWrapper createMoveClassesOrPackagesToNewDirectoryDialog(@NotNull final PsiDirectory directory,
                                                                       PsiElement[] elementsToMove,
                                                                       final MoveCallback moveCallback) {
    return new MoveClassesOrPackagesToNewDirectoryDialog(directory, elementsToMove, moveCallback);
  }

  private static void moveAsDirectory(Project project,
                                         PsiElement targetContainer,
                                         final MoveCallback callback,
                                         final PsiDirectory[] directories) {
    if (targetContainer instanceof PsiDirectory) {
      final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
      final MoveDirectoryWithClassesProcessor processor =
        new MoveDirectoryWithClassesProcessor(project, directories, (PsiDirectory)targetContainer,
                                              refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE,
                                              refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE, true, callback);
      processor.setPrepareSuccessfulSwingThreadCallback(() -> {
      });
      processor.run();
    }
    else {
      final boolean containsJava = hasJavaFiles(directories);
      if (!containsJava) {
        MoveFilesOrDirectoriesUtil.doMove(project, directories, new PsiElement[]{targetContainer}, callback);
        return;
      }
      final MoveClassesOrPackagesToNewDirectoryDialog dlg =
        new MoveClassesOrPackagesToNewDirectoryDialog(directories[0], directories, false, callback) {
          @Override
          protected BaseRefactoringProcessor createRefactoringProcessor(Project project,
                                                                        final PsiDirectory targetDirectory,
                                                                        PsiPackage aPackage,
                                                                        boolean searchInComments,
                                                                        boolean searchForTextOccurences) {
            final MoveDestination destination = createDestination(aPackage, targetDirectory);
            if (destination == null) return null;
            try {
              for (PsiDirectory dir: directories) {
                MoveFilesOrDirectoriesUtil.checkIfMoveIntoSelf(dir, WriteAction.compute(() -> destination.getTargetDirectory(dir)));
              }
            }
            catch (IncorrectOperationException e) {
              Messages.showErrorDialog(project, e.getMessage(), JavaRefactoringBundle.message("cannot.move"));
              return null;
            }
            return new MoveDirectoryWithClassesProcessor(project, directories, null, searchInComments, searchForTextOccurences, true, callback) {
              @NotNull
              @Override
              public TargetDirectoryWrapper getTargetDirectory(PsiDirectory dir) {
                final PsiDirectory targetDirectory = destination.getTargetDirectory(dir);
                return new TargetDirectoryWrapper(targetDirectory);
              }

              @Override
              protected String getTargetName() {
                return targetDirectory.getName();
              }
            };
          }
        };
      dlg.show();
    }
  }

  public static boolean hasJavaFiles(PsiDirectory... directories) {
    for (PsiDirectory directory : directories) {
      final boolean [] containsJava = new boolean[]{false};
      VfsUtil.processFileRecursivelyWithoutIgnored(directory.getVirtualFile(), file -> {
        if (FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE)) {
          containsJava[0] = true;
          return false;
        }
        return true;
      });
      if (containsJava[0]) return true;
    }
    return false;
  }

  @Override
  public PsiElement adjustTargetForMove(DataContext dataContext, PsiElement targetContainer) {
    if (targetContainer instanceof PsiPackage) {
      final Module module = LangDataKeys.TARGET_MODULE.getData(dataContext);
      if (module != null) {
        final PsiDirectory[] directories = ((PsiPackage)targetContainer).getDirectories(GlobalSearchScope.moduleScope(module));
        if (directories.length == 1) {
          return directories[0];
        }
      }
    }
    return super.adjustTargetForMove(dataContext, targetContainer);
  }

  public static boolean packageHasMultipleDirectoriesInModule(Project project, PsiDirectory targetElement) {
    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(targetElement);
    if (psiPackage != null) {
      final Module module = ModuleUtilCore.findModuleForFile(targetElement.getVirtualFile(), project);
      if (module != null && psiPackage.getDirectories(GlobalSearchScope.moduleScope(module)).length > 1) {
        return true;
      }
    }
    return false;
  }

  private static boolean canMoveOrRearrangePackages(PsiElement[] elements) {
     if (elements.length == 0) return false;
     final Project project = elements[0].getProject();
     if (JavaProjectRootsUtil.getSuitableDestinationSourceRoots(project).size() == 1) {
       return false;
     }
     for (PsiElement element : elements) {
       if (!(element instanceof PsiDirectory)) return false;
       final PsiDirectory directory = ((PsiDirectory)element);
       if (RefactoringUtil.isSourceRoot(directory)) {
         return false;
       }
       final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
       if (aPackage == null) return false;
       if (aPackage.getQualifiedName().isEmpty()) return false;
       final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(element.getProject()).getFileIndex()
         .getSourceRootForFile(directory.getVirtualFile());
       if (sourceRootForFile == null) return false;
     }
     return true;
   }

  public static boolean hasPackages(PsiDirectory directory) {
    if (JavaDirectoryService.getInstance().getPackage(directory) != null) {
      return true;
    }
    return false;
  }


  @Override
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (isPackageOrDirectory(element)) return false;
    if (isReferenceInAnonymousClass(reference)) return false;

    if (!invalid4Move(element)) {
      final PsiElement initialTargetElement = LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext);
      PsiElement[] adjustedElements = adjustForMove(project, new PsiElement[]{element}, initialTargetElement);
      if (adjustedElements == null) {
        return true;
      }
      doMoveWithMoveClassesDialog(project, adjustedElements, initialTargetElement, null);
      return true;
    }
    return false;
  }

  @Override
  public boolean isMoveRedundant(PsiElement source, PsiElement target) {
    if (target instanceof PsiDirectory && source instanceof PsiClass) {
      try {
        JavaDirectoryServiceImpl.checkCreateClassOrInterface((PsiDirectory)target, ((PsiClass)source).getName());
      }
      catch (IncorrectOperationException e) {
        return true;
      }
    }
    if (target instanceof PsiPackage && source instanceof PsiClass) {
      final GlobalSearchScope globalSearchScope = GlobalSearchScope.projectScope(source.getProject());
      return ((PsiPackage)target).findClassByShortName(Objects.requireNonNull(((PsiClass)source).getName()), globalSearchScope).length > 0;
    }
    if (target instanceof PsiDirectory && source instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)source);
      if (aPackage != null && !MoveClassesOrPackagesImpl.checkNesting(target.getProject(), aPackage, target, false)) return true;
    }
    return super.isMoveRedundant(source, target);
  }
}

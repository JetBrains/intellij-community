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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class MoveClassesOrPackagesHandlerBase extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#" + MoveClassesOrPackagesHandlerBase.class.getName());
  public static boolean isPackageOrDirectory(final PsiElement element) {
    if (element instanceof PsiPackage) return true;
    return element instanceof PsiDirectory && JavaDirectoryService.getInstance().getPackage((PsiDirectory)element) != null;
  }

  public static boolean isReferenceInAnonymousClass(@Nullable final PsiReference reference) {
    if (reference instanceof PsiJavaCodeReferenceElement &&
       ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnonymousClass) {
      return true;
    }
    return false;
  }

  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    for (PsiElement element : elements) {
      if (!isPackageOrDirectory(element)) {
        PsiFile parentFile;
        if (element instanceof PsiJavaFile) {
          final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
          if (classes.length == 0) return false;
          for (PsiClass aClass : classes) {
            if (aClass instanceof JspClass) return false;
          }
          parentFile = (PsiFile)element;
        }
        else {
          if (element instanceof JspClass) return false;
          if (!(element instanceof PsiClass)) return false;
          if (!(element.getParent() instanceof PsiFile)) return false;
          parentFile = (PsiFile)element.getParent();
        }
        if (CollectHighlightsUtil.isOutsideSourceRootJavaFile(parentFile)) return false;
      }
    }
    return super.canMove(elements, targetContainer);
  }

  @Override
  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    if (isPackageOrDirectory(psiElement)) return true;
    boolean hasPackageOrDir = false;
    for (PsiElement source : sources) {
      hasPackageOrDir |= isPackageOrDirectory(source);
    }
    return !hasPackageOrDir && psiElement instanceof PsiClass;
  }

  public PsiElement[] adjustForMove(final Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
    return MoveClassesOrPackagesImpl.adjustForMove(project,sourceElements, targetElement);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (canMoveOrRearrangePackages(elements) ) {
      final PsiDirectory[] directories = new PsiDirectory[elements.length];
      System.arraycopy(elements, 0, directories, 0, directories.length);
      SelectMoveOrRearrangePackageDialog dialog = new SelectMoveOrRearrangePackageDialog(project, directories, targetContainer == null);
      dialog.show();
      if (!dialog.isOK()) return;

      if (dialog.isPackageRearrageSelected()) {
        MoveClassesOrPackagesImpl.doRearrangePackage(project, directories);
        return;
      }

      if (dialog.isMoveDirectory()) {
        if (targetContainer instanceof PsiDirectory) {
          final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
          final MoveDirectoryWithClassesProcessor processor =
            new MoveDirectoryWithClassesProcessor(project, directories, (PsiDirectory)targetContainer,
                                                  refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE,
                                                  refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE, true, callback);
          processor.run();
        }
        else {
          final MoveClassesOrPackagesToNewDirectoryDialog dlg =
            new MoveClassesOrPackagesToNewDirectoryDialog(directories[0], new PsiElement[2], false, callback) {
              @Override
              protected void performRefactoring(Project project,
                                                final PsiDirectory targetDirectory,
                                                PsiPackage aPackage,
                                                boolean searchInComments,
                                                boolean searchForTextOccurences) {
                final MoveDirectoryWithClassesProcessor processor =
                  new MoveDirectoryWithClassesProcessor(project, directories, targetDirectory, searchInComments, searchForTextOccurences,
                                                        true, callback);
                processor.run();
              }
            };
          dlg.show();
        }
        return;
      }
    }
    if (tryDirectoryMove ( project, elements, targetContainer, callback)) {
      return;
    }
    MoveClassesOrPackagesImpl.doMove(project, elements, targetContainer, callback);
  }

  private static boolean tryDirectoryMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement, final MoveCallback callback) {
    if (targetElement instanceof PsiDirectory) {
      final PsiElement[] adjustedElements = MoveClassesOrPackagesImpl.adjustForMove(project, sourceElements, targetElement);
      if (adjustedElements != null) {
        if ( CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements),true) ) {
          new MoveClassesOrPackagesToNewDirectoryDialog((PsiDirectory)targetElement, adjustedElements, callback).show();
        }
      }
      return true;
    }
    return false;
  }

  private static boolean canMoveOrRearrangePackages(PsiElement[] elements) {
     if (elements.length == 0) return false;
     final Project project = elements[0].getProject();
     if (ProjectRootManager.getInstance(project).getContentSourceRoots().length == 1) {
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
       if ("".equals(aPackage.getQualifiedName())) return false;
       final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(element.getProject()).getFileIndex()
         .getSourceRootForFile(directory.getVirtualFile());
       if (sourceRootForFile == null) return false;
     }
     return true;
   }

   private static class SelectMoveOrRearrangePackageDialog extends DialogWrapper {
     private JRadioButton myRbMovePackage;
     private JRadioButton myRbRearrangePackage;
     private JRadioButton myRbMoveDirectory;

     private final PsiDirectory[] myDirectories;
     private final boolean myRearrangePackagesEnabled;

     public SelectMoveOrRearrangePackageDialog(Project project, PsiDirectory[] directories) {
       this(project, directories, true);
     }

     public SelectMoveOrRearrangePackageDialog(Project project, PsiDirectory[] directories, boolean rearrangePackagesEnabled) {
       super(project, true);
       myDirectories = directories;
       myRearrangePackagesEnabled = rearrangePackagesEnabled;
       setTitle(RefactoringBundle.message("select.refactoring.title"));
       init();
     }

     protected JComponent createNorthPanel() {
       return new JLabel(RefactoringBundle.message("what.would.you.like.to.do"));
     }

     public JComponent getPreferredFocusedComponent() {
       return myRbMovePackage;
     }

     protected String getDimensionServiceKey() {
       return "#com.intellij.refactoring.move.MoveHandler.SelectRefactoringDialog";
     }


     protected JComponent createCenterPanel() {
       JPanel panel = new JPanel(new BorderLayout());


       final HashSet<String> packages = new HashSet<String>();
       for (PsiDirectory directory : myDirectories) {
         packages.add(JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName());
       }
       final String moveDescription;
       LOG.assertTrue(myDirectories.length > 0);
       LOG.assertTrue(packages.size() > 0);
       if (packages.size() > 1) {
         moveDescription = RefactoringBundle.message("move.packages.to.another.package", packages.size());
       }
       else {
         final String qName = packages.iterator().next();
         moveDescription = RefactoringBundle.message("move.package.to.another.package", qName);
       }

       myRbMovePackage = new JRadioButton();
       myRbMovePackage.setText(moveDescription);
       myRbMovePackage.setSelected(true);

       final String rearrangeDescription;
       if (myDirectories.length > 1) {
         rearrangeDescription = RefactoringBundle.message("move.directories.to.another.source.root", myDirectories.length);
       }
       else {
         rearrangeDescription = RefactoringBundle.message("move.directory.to.another.source.root", myDirectories[0].getVirtualFile().getPresentableUrl());
       }
       myRbRearrangePackage = new JRadioButton();
       myRbRearrangePackage.setText(rearrangeDescription);
       myRbRearrangePackage.setVisible(myRearrangePackagesEnabled);

       final String moveDirectoryDescription;
       if (myDirectories.length > 1) {
         moveDirectoryDescription = "Move everything from " + myDirectories.length + " directories to another directory";
       }
       else {
         moveDirectoryDescription = "Move everything from " + myDirectories[0].getVirtualFile().getPresentableUrl() + " to another directory";
       }
       myRbMoveDirectory = new JRadioButton();
       myRbMoveDirectory.setMnemonic('d');
       myRbMoveDirectory.setText(moveDirectoryDescription);

       ButtonGroup gr = new ButtonGroup();
       gr.add(myRbMovePackage);
       gr.add(myRbRearrangePackage);
       gr.add(myRbMoveDirectory);

       new RadioUpDownListener(myRbMovePackage, myRbRearrangePackage, myRbMoveDirectory);

       Box box = Box.createVerticalBox();
       box.add(Box.createVerticalStrut(5));
       box.add(myRbMovePackage);
       box.add(myRbRearrangePackage);
       box.add(myRbMoveDirectory);
       panel.add(box, BorderLayout.CENTER);
       return panel;
     }

     public boolean isPackageRearrageSelected() {
       return myRbRearrangePackage.isSelected();
     }

     public boolean isMoveDirectory() {
       return myRbMoveDirectory.isSelected();
     }
   }


  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (isPackageOrDirectory(element)) return false;
    if (CollectHighlightsUtil.isOutsideSourceRootJavaFile(element.getContainingFile())) return false;
    if (isReferenceInAnonymousClass(reference)) return false;

    if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass) && element.getParent() instanceof PsiFile) {
      MoveClassesOrPackagesImpl.doMove(project, new PsiElement[]{element},
                                       LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext), null);
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
    return super.isMoveRedundant(source, target);
  }
}

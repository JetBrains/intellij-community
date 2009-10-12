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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class MovePackagesHandler extends MoveClassesOrPackagesHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MovePackagesHandler");

  public boolean canMove(final PsiElement[] elements, final PsiElement targetContainer) {
    for(PsiElement element: elements) {
      if (!isPackageOrDirectory(element)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return isPackageOrDirectory(psiElement);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (targetContainer == null && canMoveOrRearrangePackages(elements) ) {
      PsiDirectory[] directories = new PsiDirectory[elements.length];
      System.arraycopy(elements, 0, directories, 0, directories.length);
      SelectMoveOrRearrangePackageDialog dialog = new SelectMoveOrRearrangePackageDialog(project, directories);
      dialog.show();
      if (!dialog.isOK()) return;

      if (dialog.isPackageRearrageSelected()) {
        MoveClassesOrPackagesImpl.doRearrangePackage(project, directories);
        return;
      }

      if (dialog.isMoveDirectory()) {
        final ArrayList<MoveRenameUsageInfo> usages = new ArrayList<MoveRenameUsageInfo>();
        for (PsiDirectory directory : directories) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
          if (aPackage != null) {
            for (PsiReference reference : ReferencesSearch.search(aPackage, directory.getUseScope())) {
              usages.add(new MoveRenameUsageInfo(reference, aPackage));
            }
          }
        }

        final PsiElement[] targetDirectory = new PsiElement[]{targetContainer};
        
        MoveFilesOrDirectoriesUtil.doMove(project, elements, targetDirectory, new MoveCallback() {
          public void refactoringCompleted() {
            if (callback != null) callback.refactoringCompleted();
            if (targetDirectory[0] instanceof PsiDirectory) {
              final PsiPackage destPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)targetDirectory[0]);
              if (destPackage != null) {
                for (MoveRenameUsageInfo usage : usages) {
                  final PsiPackage aPackage = (PsiPackage)usage.getReferencedElement();
                  if (aPackage != null) {
                    final PsiReference reference = usage.getReference();
                    if (reference != null) {
                      final PsiPackage pack = JavaPsiFacade.getInstance(project).findPackage(StringUtil.getQualifiedName(destPackage.getQualifiedName(), aPackage.getName()));
                      if (pack != null) {
                        reference.bindToElement(pack);
                      }
                    }
                  }
                }
              }
            }
          }
        });
        return;
      }
    }
    super.doMove(project, elements, targetContainer, callback);
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

    public SelectMoveOrRearrangePackageDialog(Project project, PsiDirectory[] directories) {
      super(project, true);
      myDirectories = directories;
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

      final String moveDirectoryDescription;
      if (myDirectories.length > 1) {
        moveDirectoryDescription = "Move " + myDirectories.length + " directories to another directory";
      }
      else {
        moveDirectoryDescription = "Move directory " + myDirectories[0].getVirtualFile().getPresentableUrl() + " to another directory";
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
}

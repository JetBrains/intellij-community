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
package com.intellij.refactoring.copy;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

class CopyClassDialog extends DialogWrapper{
  @NonNls private static final String RECENTS_KEY = "CopyClassDialog.RECENTS_KEY";
  private final JLabel myInformationLabel = new JLabel();
  private final JLabel myNameLabel = new JLabel();
  private EditorTextField myNameField;
  private final JLabel myPackageLabel = new JLabel();
  private ReferenceEditorComboWithBrowseButton myTfPackage;
  private final Project myProject;
  private PsiDirectory myTargetDirectory;
  private final boolean myDoClone;
  private final PsiDirectory myDefaultTargetDirectory;
  private final JCheckBox myCbMoveToAnotherSourceFolder = new JCheckBox(RefactoringBundle.message("move.classes.move.to.another.source.folder"));

  public CopyClassDialog(PsiClass aClass, PsiDirectory defaultTargetDirectory, Project project, boolean doClone) {
    super(project, true);
    myProject = project;
    myDefaultTargetDirectory = defaultTargetDirectory;
    init();
    myDoClone = doClone;
    String text = myDoClone ? RefactoringBundle.message("copy.class.clone.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass)) :
                       RefactoringBundle.message("copy.class.copy.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass));
    myInformationLabel.setText(text);
    myNameField.setText(UsageViewUtil.getShortName(aClass));
    myNameLabel.setText(RefactoringBundle.message("name.prompt"));
    if (myDoClone) {
      myTfPackage.setVisible(false);
      myPackageLabel.setVisible(false);
      myCbMoveToAnotherSourceFolder.setVisible(false);
    }
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected JComponent createCenterPanel() {
    return new JPanel(new BorderLayout());
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createRoundedBorder());

    gbConstraints.insets = new Insets(4,8,4,8);
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myInformationLabel, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.gridy = 1;
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 0;
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    myNameField = new EditorTextField("");
    panel.add(myNameField, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    panel.add(myPackageLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    String qualifiedName = "";
    if (myDefaultTargetDirectory != null) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(myDefaultTargetDirectory);
      if (aPackage != null) {
        qualifiedName = aPackage.getQualifiedName();
      }
    }

    myTfPackage = new PackageNameReferenceEditorCombo(qualifiedName, myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
    if (qualifiedName.length() > 0) {
      myTfPackage.setTextFieldPreferredWidth(qualifiedName.length() + 5);
    }

    myPackageLabel.setText(RefactoringBundle.message("destination.package"));

    panel.add(myTfPackage, gbConstraints);

    myCbMoveToAnotherSourceFolder.setEnabled(ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1);
    gbConstraints.gridy = 3;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.NONE;
    panel.add(myCbMoveToAnotherSourceFolder, gbConstraints);

    return panel;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public String getClassName() {
    return myNameField.getText();
  }

  protected void doOKAction(){
    final String packageName = myTfPackage.getText();
    final String className = getClassName();

    final String[] errorString = new String[1];
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(manager.getProject()).getNameHelper();
    if (packageName.length() > 0 && !nameHelper.isQualifiedName(packageName)) {
      errorString[0] = RefactoringBundle.message("invalid.target.package.name.specified");
    } else if ("".equals(className)) {
      errorString[0] = RefactoringBundle.message("no.class.name.specified");
    } else {
      if (!nameHelper.isIdentifier(className)) {
        errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
      }
      else if (!myDoClone) {
        try {
          if (myCbMoveToAnotherSourceFolder.isSelected() && myCbMoveToAnotherSourceFolder.isEnabled()) {
            final PackageWrapper targetPackage = new PackageWrapper(manager, packageName);
            final VirtualFile sourceRoot = MoveClassesOrPackagesUtil
              .chooseSourceRoot(targetPackage, ProjectRootManager.getInstance(myProject).getContentSourceRoots(), myDefaultTargetDirectory);
            if (sourceRoot == null) return;
            new WriteCommandAction(myProject, CodeInsightBundle.message("create.directory.command")){
              @Override
              protected void run(Result objectResult) throws Throwable {
                myTargetDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(targetPackage, sourceRoot);
              }
            }.execute();
          } else {
            final Module module = ModuleUtil.findModuleForFile(myDefaultTargetDirectory.getVirtualFile(), myProject);
            if (module != null) {
              myTargetDirectory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, myDefaultTargetDirectory, true);
            } else {
              errorString[0] = "No module found for directory \'" + myDefaultTargetDirectory.getVirtualFile().getPresentableUrl() + "\'";
            }
          }
          if (myTargetDirectory == null) {
            if (errorString[0] == null) {
              errorString[0] = ""; // message already reported by PackageUtil
            }
          } else {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                    errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, className);
                  }
                });
              }
            }, RefactoringBundle.message("create.directory"), null);
          }
        }
        catch (IncorrectOperationException e) {
          errorString[0] = e.getMessage();
        }
      }
      RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    }

    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        Messages.showMessageDialog(myProject, errorString[0], RefactoringBundle.message("error.title"), Messages.getErrorIcon());
      }
      myNameField.requestFocusInWindow();
      return;
    }
    super.doOKAction();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.COPY_CLASS);
  }
}

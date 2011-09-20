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

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.components.JBLabel;
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
  private final boolean myDoClone;
  private final PsiDirectory myDefaultTargetDirectory;
  private final DestinationFolderComboBox myDestinationCB = new DestinationFolderComboBox() {
    @Override
    public String getTargetPackage() {
      return myTfPackage.getText().trim();
    }

    @Override
    protected boolean reportBaseInTestSelectionInSource() {
      return true;
    }
  };
  protected MoveDestination myDestination;

  public CopyClassDialog(PsiClass aClass, PsiDirectory defaultTargetDirectory, Project project, boolean doClone) {
    super(project, true);
    myProject = project;
    myDefaultTargetDirectory = defaultTargetDirectory;
    myDoClone = doClone;
    String text = myDoClone ? RefactoringBundle.message("copy.class.clone.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass)) :
                  RefactoringBundle.message("copy.class.copy.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass));
    myInformationLabel.setText(text);
    init();
    myDestinationCB.setData(myProject, defaultTargetDirectory,
                            new Pass<String>() {
                              @Override
                              public void pass(String s) {
                                setErrorText(s);
                              }
                            }, myTfPackage.getChildComponent());
    myNameField.setText(UsageViewUtil.getShortName(aClass));
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
    myNameLabel.setText(RefactoringBundle.message("copy.files.new.name.label"));
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    myNameField = new EditorTextField("");
    myNameLabel.setLabelFor(myNameField);
    panel.add(myNameField, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    panel.add(myPackageLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    String qualifiedName = getQualifiedName();

    myTfPackage = new PackageNameReferenceEditorCombo(qualifiedName, myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
    myTfPackage.setTextFieldPreferredWidth(Math.max(qualifiedName.length() + 5, 40));

    myPackageLabel.setText(RefactoringBundle.message("destination.package"));
    myPackageLabel.setLabelFor(myTfPackage);

    panel.add(myTfPackage, gbConstraints);

    final JBLabel label = new JBLabel(RefactoringBundle.message("target.destination.folder"));
    if (myDoClone) {
      myTfPackage.setVisible(false);
      myPackageLabel.setVisible(false);
    }
    final boolean isMultipleSourceRoots = ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1;
    myDestinationCB.setVisible(!myDoClone && isMultipleSourceRoots);
    label.setVisible(!myDoClone && isMultipleSourceRoots);

    gbConstraints.gridy = 3;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.insets.top = 12;
    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.NONE;
    panel.add(label, gbConstraints);

    gbConstraints.gridy = 4;
    gbConstraints.gridx = 0;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets.top = 4;
    panel.add(myDestinationCB, gbConstraints);

    return panel;
  }

  protected String getQualifiedName() {
    String qualifiedName = "";
    if (myDefaultTargetDirectory != null) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(myDefaultTargetDirectory);
      if (aPackage != null) {
        qualifiedName = aPackage.getQualifiedName();
      }
    }
    return qualifiedName;
  }

  public MoveDestination getTargetDirectory() {
    return myDestination;
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
          final PackageWrapper targetPackage = new PackageWrapper(manager, packageName);
          myDestination = myDestinationCB.selectDirectory(targetPackage, false);
          if (myDestination == null) return;
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

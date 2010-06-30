/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.RecentsManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseDialog<ClassType extends PsiElement, MemberType> extends RefactoringDialog {
  protected String myRefactoringName;
  protected ClassType mySourceClass;
  protected PsiDirectory myTargetDirectory;
  protected List<MemberType> myMemberInfos;

  protected JRadioButton myRbExtractSuperclass;
  protected JRadioButton myRbExtractSubclass;

  protected JTextField mySourceClassField;
  protected JTextField myExtractedSuperNameField;
  protected ComponentWithBrowseButton<EditorComboBox> myPackageNameField;
  protected DocCommentPanel myDocCommentPanel;

  protected abstract void initPackageNameField();

  protected abstract void initSourceClassField();


  protected abstract String getDocCommentPanelName();

  protected abstract String getExtractedSuperNameNotSpecifiedKey();

  protected boolean checkConflicts() {
    return true;
  }

  protected abstract BaseRefactoringProcessor createProcessor();

  protected abstract int getDocCommentPolicySetting();

  protected abstract void setDocCommentPolicySetting(int policy);

  protected abstract String getHelpId();

  @Nullable
  protected abstract String validateName(String name);

  protected abstract String getClassNameLabelText();

  protected abstract JLabel getClassNameLabel();

  protected abstract JLabel getPackageNameLabel();

  protected abstract String getPackageNameLabelText();

  protected abstract String getEntityName();

  protected abstract void preparePackage() throws OperationFailedException;

  protected abstract String getDestinationPackageRecentKey();

  public ExtractSuperBaseDialog(Project project, ClassType sourceClass, List<MemberType> members, String refactoringName) {
    super(project, true);
    myRefactoringName = refactoringName;

    mySourceClass = sourceClass;
    myMemberInfos = members;
    myTargetDirectory = mySourceClass.getContainingFile().getContainingDirectory();
  }

  @Override
  protected void init() {
    setTitle(myRefactoringName);

    initPackageNameField();
    initSourceClassField();
    myExtractedSuperNameField = new JTextField();

    myDocCommentPanel = new DocCommentPanel(getDocCommentPanelName());
    myDocCommentPanel.setPolicy(getDocCommentPolicySetting());

    super.init();
    updateDialogForExtractSuperclass();
  }

  protected JComponent createActionComponent() {
    Box box = Box.createHorizontalBox();
    final String s = StringUtil.decapitalize(getEntityName());
    myRbExtractSuperclass = new JRadioButton();
    myRbExtractSuperclass.setText(RefactoringBundle.message("extractSuper.extract", s));
    myRbExtractSubclass = new JRadioButton();
    myRbExtractSubclass.setText(RefactoringBundle.message("extractSuper.rename.original.class", s));
    box.add(myRbExtractSuperclass);
    box.add(myRbExtractSubclass);
    box.add(Box.createHorizontalGlue());
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbExtractSuperclass);
    buttonGroup.add(myRbExtractSubclass);
    myRbExtractSuperclass.setSelected(true);
    myRbExtractSuperclass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateDialogForExtractSuperclass();
      }
    });

    myRbExtractSubclass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateDialogForExtractSubclass();
      }
    });
    return box;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myExtractedSuperNameField;
  }

  protected void updateDialogForExtractSubclass() {
    getClassNameLabel().setText(RefactoringBundle.message("extractSuper.rename.original.class.to"));
    getPackageNameLabel().setText(getPackageNameLabelText());
    getPreviewAction().setEnabled(true);
  }

  protected void updateDialogForExtractSuperclass() {
    getClassNameLabel().setText(getClassNameLabelText());
    getPackageNameLabel().setText(getPackageNameLabelText());
    getPreviewAction().setEnabled(false);
  }

  public String getExtractedSuperName() {
    return myExtractedSuperNameField.getText().trim();
  }

  protected String getTargetPackageName() {
    return myPackageNameField.getChildComponent().getText().trim();
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public int getDocCommentPolicy() {
    return myDocCommentPanel.getPolicy();
  }

  public boolean isExtractSuperclass() {
    return myRbExtractSuperclass.isSelected();
  }

  protected void doAction() {
    final String[] errorString = new String[]{null};
    final String extractedSuperName = getExtractedSuperName();
    final String packageName = getTargetPackageName();
    RecentsManager.getInstance(myProject).registerRecentEntry(getDestinationPackageRecentKey(), packageName);

    if ("".equals(extractedSuperName)) {
      errorString[0] = getExtractedSuperNameNotSpecifiedKey();
      myExtractedSuperNameField.requestFocusInWindow();
    }
    else {
      String nameError = validateName(extractedSuperName);
      if (nameError != null) {
        errorString[0] = nameError;
        myExtractedSuperNameField.requestFocusInWindow();
      }
      else {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            try {
              preparePackage();
            }
            catch (IncorrectOperationException e) {
              errorString[0] = e.getMessage();
              myPackageNameField.requestFocusInWindow();
            }
            catch (OperationFailedException e) {
              errorString[0] = e.getMessage();
              myPackageNameField.requestFocusInWindow();
            }
          }
        }, RefactoringBundle.message("create.directory"), null);
      }
    }
    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        CommonRefactoringUtil.showErrorMessage(myRefactoringName, errorString[0], getHelpId(), myProject);
      }
      return;
    }

    if (!checkConflicts()) return;

    if (!isExtractSuperclass()) {
      invokeRefactoring(createProcessor());
    }
    setDocCommentPolicySetting(getDocCommentPolicy());
    closeOKAction();
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected static class OperationFailedException extends Exception {
    protected OperationFailedException(String message) {
      super(message);
    }
  }
}

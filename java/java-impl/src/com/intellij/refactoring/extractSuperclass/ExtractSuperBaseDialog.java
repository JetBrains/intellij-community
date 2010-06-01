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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.ide.util.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.help.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classMembers.*;
import com.intellij.ui.*;
import com.intellij.util.*;

import javax.swing.*;
import java.awt.event.*;
import java.util.*;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseDialog extends RefactoringDialog {
  protected String myRefactoringName;
  protected PsiClass mySourceClass;
  protected PsiDirectory myTargetDirectory;
  protected List<MemberInfo> myMemberInfos;

  protected JRadioButton myRbExtractSuperclass;
  protected JRadioButton myRbExtractSubclass;

  protected JTextField mySourceClassField;
  protected JTextField myExtractedSuperNameField;
  protected PackageNameReferenceEditorCombo myPackageNameField;
  protected DocCommentPanel myJavaDocPanel;
  private static final String DESTINATION_PACKAGE_RECENT_KEY = "ExtractSuperBase.RECENT_KEYS";


  public ExtractSuperBaseDialog(Project project, PsiClass sourceClass, List<MemberInfo> members, String refactoringName) {
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

    myJavaDocPanel = new DocCommentPanel(getJavaDocPanelName());
    myJavaDocPanel.setPolicy(getJavaDocPolicySetting());

    super.init();
    updateDialogForExtractSuperclass();
  }

  private void initPackageNameField() {
    String name = "";
    PsiFile file = mySourceClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      name = ((PsiJavaFile)file).getPackageName();
    }
    myPackageNameField = new PackageNameReferenceEditorCombo(name, myProject, DESTINATION_PACKAGE_RECENT_KEY, RefactoringBundle.message("choose.destination.package"));
  }

  private void initSourceClassField() {
    mySourceClassField = new JTextField();
    mySourceClassField.setEditable(false);
    mySourceClassField.setText(mySourceClass.getQualifiedName());
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
    return myPackageNameField.getText().trim();
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  protected abstract String getClassNameLabelText();

  protected abstract JLabel getClassNameLabel();

  protected abstract JLabel getPackageNameLabel();

  protected abstract String getPackageNameLabelText();

  protected abstract String getEntityName();

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public boolean isExtractSuperclass() {
    return myRbExtractSuperclass.isSelected();
  }

  protected void doAction() {
      final String[] errorString = new String[]{null};
      final String extractedSuperName = getExtractedSuperName();
      final String packageName = getTargetPackageName();
      RecentsManager.getInstance(myProject).registerRecentEntry(DESTINATION_PACKAGE_RECENT_KEY, packageName);
      final PsiManager manager = PsiManager.getInstance(myProject);

      if ("".equals(extractedSuperName)) {
        errorString[0] = getExtractedSuperNameNotSpecifiedKey();
        myExtractedSuperNameField.requestFocusInWindow();
      }
      else {
        if (!JavaPsiFacade.getInstance(manager.getProject()).getNameHelper().isIdentifier(extractedSuperName)) {
          errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(extractedSuperName);
          myExtractedSuperNameField.requestFocusInWindow();
        }
        else {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              try {
                final PsiPackage aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(packageName);
                if (aPackage != null) {
                  final PsiDirectory[] directories = aPackage.getDirectories(mySourceClass.getResolveScope());
                  if (directories.length >= 1) {
                    myTargetDirectory = getDirUnderSameSourceRoot(directories);
                  }
                }
                myTargetDirectory
                  = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myTargetDirectory, true);
                if (myTargetDirectory == null) {
                  errorString[0] = ""; // message already reported by PackageUtil
                  return;
                }
                errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, extractedSuperName);
              }
              catch (IncorrectOperationException e) {
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
      setJavaDocPolicySetting(getJavaDocPolicy());
      closeOKAction();
    }

  private PsiDirectory getDirUnderSameSourceRoot(final PsiDirectory[] directories) {
    final VirtualFile sourceFile = mySourceClass.getContainingFile().getVirtualFile();
    if (sourceFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(sourceFile);
      if (sourceRoot != null) {
        for(PsiDirectory dir: directories) {
          if (fileIndex.getSourceRootForFile(dir.getVirtualFile()) == sourceRoot) {
            return dir;
          }
        }
      }
    }
    return directories[0];
  }

  protected abstract String getJavaDocPanelName();

  protected abstract String getExtractedSuperNameNotSpecifiedKey();
  protected boolean checkConflicts() { return true; }
  protected abstract ExtractSuperBaseProcessor createProcessor();

  protected abstract int getJavaDocPolicySetting();
  protected abstract void setJavaDocPolicySetting(int policy);

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected abstract String getHelpId();
}

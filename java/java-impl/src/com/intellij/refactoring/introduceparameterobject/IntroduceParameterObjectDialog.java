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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.AbstractIntroduceParameterObjectDialog;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
public class IntroduceParameterObjectDialog extends AbstractIntroduceParameterObjectDialog<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor, VariableData> {


  private JRadioButton useExistingClassButton;
  private JPanel myUseExistingPanel;

  private JRadioButton createNewClassButton;
  private JTextField classNameField;

  private JPanel myCreateNewClassPanel;

  private JRadioButton myCreateInnerClassRadioButton;
  private JTextField myInnerClassNameTextField;
  private JPanel myInnerClassPanel;

  private JPanel myWholePanel;
  private ReferenceEditorComboWithBrowseButton packageTextField;
  private ReferenceEditorComboWithBrowseButton existingClassField;
  private JCheckBox myGenerateAccessorsCheckBox;
  private JCheckBox myEscalateVisibilityCheckBox;
  private ComboboxWithBrowseButton myDestinationCb;
  private static final String RECENTS_KEY = "IntroduceParameterObject.RECENTS_KEY";
  private static final String EXISTING_KEY = "IntroduceParameterObject.EXISTING_KEY";

  public IntroduceParameterObjectDialog(PsiMethod sourceMethod) {
    super(sourceMethod);
    final DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    };
    final PsiClass containingClass = sourceMethod.getContainingClass();
    classNameField.getDocument().addDocumentListener(docListener);
    myInnerClassNameTextField.getDocument().addDocumentListener(docListener);

    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(useExistingClassButton);
    buttonGroup.add(createNewClassButton);
    buttonGroup.add(myCreateInnerClassRadioButton);
    createNewClassButton.setSelected(true);
    if (containingClass == null ||
        containingClass.getQualifiedName() == null ||
        containingClass.getContainingClass() != null ||
        containingClass.isInterface()) {
      myCreateInnerClassRadioButton.setEnabled(false);
    }
    init();

    final ActionListener listener = actionEvent -> {
      toggleRadioEnablement();
      final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
      if (useExistingClass()) {
        focusManager.requestFocus(existingClassField, true);
      }
      else if (myCreateInnerClassRadioButton.isSelected()) {
        focusManager.requestFocus(myInnerClassNameTextField, true);
      }
      else {
        focusManager.requestFocus(classNameField, true);
      }
    };
    useExistingClassButton.addActionListener(listener);
    createNewClassButton.addActionListener(listener);
    myCreateInnerClassRadioButton.addActionListener(listener);
    myGenerateAccessorsCheckBox.setSelected(true);
    myEscalateVisibilityCheckBox.setSelected(true);
    toggleRadioEnablement();
  }

  @Override
  protected boolean isDelegateCheckboxVisible() {
    final PsiClass containingClass = mySourceMethod.getContainingClass();
    return containingClass != null && !containingClass.isInterface();
  }

  private void toggleRadioEnablement() {
    UIUtil.setEnabled(myUseExistingPanel, useExistingClassButton.isSelected(), true);
    UIUtil.setEnabled(myCreateNewClassPanel, createNewClassButton.isSelected(), true);
    UIUtil.setEnabled(myInnerClassPanel, myCreateInnerClassRadioButton.isSelected(), true);
    validateButtons();
    enableGenerateAccessors();
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.IntroduceParameterObject";
  }

  protected String getSourceMethodPresentation() {
    return PsiFormatUtil.formatMethod(mySourceMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME, 0);
  }

  protected ParameterTablePanel createParametersPanel() {
    final PsiParameterList parameterList = mySourceMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    VariableData[] parameterInfo = new VariableData[parameters.length];
    for (int i = 0; i < parameterInfo.length; i++) {
      parameterInfo[i] = new VariableData(parameters[i]);
      parameterInfo[i].name = parameters[i].getName();
      parameterInfo[i].passAsParameter = true;
    }
    return new ParameterTablePanel(myProject, parameterInfo) {
      protected void updateSignature() {}

      protected void doEnterAction() {}

      protected void doCancelAction() {
        IntroduceParameterObjectDialog.this.doCancelAction();
      }
    };
  }

  @Override
  protected JPanel createParameterClassPanel() {
    return myWholePanel;
  }

  @Override
  protected JavaIntroduceParameterObjectClassDescriptor createClassDescriptor() {
    final boolean useExistingClass = useExistingClass();
    final String className;
    final String packageName;
    final boolean createInnerClass = myCreateInnerClassRadioButton.isSelected();
    if (createInnerClass) {
      className = getInnerClassName();
      packageName = "";
    } else if (useExistingClass) {
      final String existingClassName = getExistingClassName();
      className = StringUtil.getShortName(existingClassName);
      packageName = StringUtil.getPackageName(existingClassName);
    }
    else {
      packageName = getPackageName();
      className = getClassName();
    }

    final String newVisibility =
      myEscalateVisibilityCheckBox.isEnabled() && myEscalateVisibilityCheckBox.isSelected() ? VisibilityUtil.ESCALATE_VISIBILITY : null;
    final MoveDestination moveDestination = ((DestinationFolderComboBox)myDestinationCb)
      .selectDirectory(new PackageWrapper(PsiManager.getInstance(myProject), packageName), false);
    final PsiParameterList parameterList = mySourceMethod.getParameterList();
    final List<ParameterInfoImpl> parameters = new ArrayList<>();
    for (VariableData data : myParameterTablePanel.getVariableData()) {
      if (data.passAsParameter) {
        parameters.add(new ParameterInfoImpl(parameterList.getParameterIndex((PsiParameter)data.variable), data.name, data.type));
      }
    }
    final ParameterInfoImpl[] infos = parameters.toArray(new ParameterInfoImpl[parameters.size()]);
    return new JavaIntroduceParameterObjectClassDescriptor(className, packageName, moveDestination, useExistingClass, createInnerClass,
                                                           newVisibility, infos, mySourceMethod,
                                                           myGenerateAccessorsCheckBox.isSelected());
  }

  @Override
  protected void canRun() throws ConfigurationException {
    super.canRun();
    final Project project = mySourceMethod.getProject();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    if (myCreateInnerClassRadioButton.isSelected()) {
      final String innerClassName = getInnerClassName();
      if (!nameHelper.isIdentifier(innerClassName)) throw new ConfigurationException("\'" + innerClassName + "\' is invalid inner class name");
      if (mySourceMethod.getContainingClass().findInnerClassByName(innerClassName, false) != null) throw new ConfigurationException("Inner class with name \'" + innerClassName + "\' already exist");
    } else if (!useExistingClass()) {
      final String className = getClassName();
      if (className.length() == 0 || !nameHelper.isIdentifier(className)) {
        throw new ConfigurationException("\'" + className + "\' is invalid parameter class name");
      }
      final String packageName = getPackageName();

      if (packageName.length() == 0 || !nameHelper.isQualifiedName(packageName)) {
        throw new ConfigurationException("\'" + packageName + "\' is invalid parameter class package name");
      }
    }
    else {
      final String className = getExistingClassName();
      if (className.length() == 0 || !nameHelper.isQualifiedName(className)) {
        throw new ConfigurationException("\'" + className + "\' is invalid qualified parameter class name");
      }
      if (JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject())) == null) {
        throw new ConfigurationException("\'" + className + "\' does not exist");
      }
    }
  }

  private String getInnerClassName() {
    return  myInnerClassNameTextField.getText().trim();
  }

  @NotNull
  public String getPackageName() {
    return packageTextField.getText().trim();
  }

  @NotNull
  public String getExistingClassName() {
    return existingClassField.getText().trim();
  }

  @NotNull
  public String getClassName() {
    return classNameField.getText().trim();
  }

  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  protected void doHelpAction() {
    final HelpManager helpManager = HelpManager.getInstance();
    helpManager.invokeHelp(HelpID.IntroduceParameterObject);
  }

  public boolean useExistingClass() {
    return useExistingClassButton.isSelected();
  }

  private void createUIComponents() {
    final PsiFile file = mySourceMethod.getContainingFile();
    packageTextField =
          new PackageNameReferenceEditorCombo(file instanceof PsiJavaFile ? ((PsiJavaFile)file).getPackageName() : "", myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
        final Document document = packageTextField.getChildComponent().getDocument();
    final com.intellij.openapi.editor.event.DocumentAdapter adapter = new com.intellij.openapi.editor.event.DocumentAdapter() {
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        validateButtons();
      }
    };
    document.addDocumentListener(adapter);

    existingClassField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = mySourceMethod.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final TreeJavaClassChooserDialog chooser =
          new TreeJavaClassChooserDialog(RefactorJBundle.message("select.wrapper.class"), project, scope, null, null);
        final String classText = existingClassField.getText();
        final PsiClass currentClass = JavaPsiFacade.getInstance(project).findClass(classText, GlobalSearchScope.allScope(project));
        if (currentClass != null) {
          chooser.select(currentClass);
        }
        chooser.show();
        final PsiClass selectedClass = chooser.getSelected();
        if (selectedClass != null) {
          final String className = selectedClass.getQualifiedName();
          existingClassField.setText(className);
          RecentsManager.getInstance(myProject).registerRecentEntry(EXISTING_KEY, className);
        }
      }
    }, "", myProject, true, EXISTING_KEY);

    existingClassField.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        validateButtons();
        enableGenerateAccessors();
      }
    });
    myDestinationCb = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return getPackageName();
      }
    };
    ((DestinationFolderComboBox)myDestinationCb).setData(myProject, mySourceMethod.getContainingFile().getContainingDirectory(), packageTextField.getChildComponent());
  }

  private void enableGenerateAccessors() {
    boolean existingNotALibraryClass = false;
    if (useExistingClassButton.isSelected()) {
      final PsiClass selectedClass =
        JavaPsiFacade.getInstance(myProject).findClass(existingClassField.getText(), GlobalSearchScope.projectScope(myProject));
      if (selectedClass != null) {
        final PsiFile containingFile = selectedClass.getContainingFile();
        if (containingFile != null) {
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          if (virtualFile != null) {
            existingNotALibraryClass = ProjectRootManager.getInstance(myProject).getFileIndex().isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES);
          }
        }
      }
    }
    myGenerateAccessorsCheckBox.setEnabled(existingNotALibraryClass);
    myEscalateVisibilityCheckBox.setEnabled(existingNotALibraryClass);
  }
}

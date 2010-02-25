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
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class WrapReturnValueDialog extends RefactoringDialog {

  private final PsiMethod sourceMethod;
  private JTextField sourceMethodTextField;

  private JRadioButton createNewClassButton;
  private JTextField classNameField;
  private PackageNameReferenceEditorCombo packageTextField;
  private JPanel myNewClassPanel;

  private ReferenceEditorComboWithBrowseButton existingClassField;
  private JRadioButton useExistingClassButton;
  private JComboBox myFieldsCombo;
  private JPanel myExistingClassPanel;

  private JPanel myWholePanel;

  private JRadioButton myCreateInnerClassButton;
  private JTextField myInnerClassNameTextField;
  private JPanel myCreateInnerPanel;
  private static final String RECENT_KEYS = "WrapReturnValue.RECENT_KEYS";

  WrapReturnValueDialog(PsiMethod sourceMethod) {
    super(sourceMethod.getProject(), true);
    this.sourceMethod = sourceMethod;
    setTitle(RefactorJBundle.message("wrap.return.value.title"));
    init();
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.WrapReturnValue";
  }

  protected void doAction() {
    final boolean useExistingClass = useExistingClassButton.isSelected();
    final boolean createInnerClass = myCreateInnerClassButton.isSelected();
    final String existingClassName = existingClassField.getText().trim();
    final String className;
    final String packageName;
    if (useExistingClass) {
      className = StringUtil.getShortName(existingClassName);
      packageName = StringUtil.getPackageName(existingClassName);
    }
    else if (createInnerClass) {
      className = getInnerClassName();
      packageName = "";
    }
    else {
      className = getClassName();
      packageName = getPackageName();
    }
    invokeRefactoring(
      new WrapReturnValueProcessor(className, packageName, sourceMethod, useExistingClass, createInnerClass, (PsiField)myFieldsCombo.getSelectedItem()));
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final Project project = sourceMethod.getProject();
    final JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
    final PsiNameHelper nameHelper = manager.getNameHelper();
    if (myCreateInnerClassButton.isSelected()) {
      final String innerClassName = getInnerClassName().trim();
      if (!nameHelper.isIdentifier(innerClassName)) throw new ConfigurationException("\'" + StringUtil.first(innerClassName, 10, true) + "\' is invalid inner class name");
      if (sourceMethod.getContainingClass().findInnerClassByName(innerClassName, false) != null) throw new ConfigurationException("Inner class with name \'" + StringUtil.first(innerClassName, 10, true) + "\' already exist");
    } else if (useExistingClassButton.isSelected()) {
      final String className = existingClassField.getText().trim();
      if (className.length() == 0 || !nameHelper.isQualifiedName(className)) {
        throw new ConfigurationException("\'" + StringUtil.last(className, 10, true) + "\' is invalid qualified wrapper class name");
      }
      final Object item = myFieldsCombo.getSelectedItem();
      if (item == null) {
        throw new ConfigurationException("Wrapper field not found");
      }
    } else {
      final String className = getClassName();
      if (className.length() == 0 || !nameHelper.isIdentifier(className)) {
        throw new ConfigurationException("\'" + StringUtil.first(className, 10, true) + "\' is invalid wrapper class name");
      }
      final String packageName = getPackageName();

      if (packageName.length() == 0 || !nameHelper.isQualifiedName(packageName)) {
        throw new ConfigurationException("\'" + StringUtil.last(packageName, 10, true) + "\' is invalid wrapper class package name");
      }
    }
  }

  private String getInnerClassName() {
    return myInnerClassNameTextField.getText().trim();
  }

  @NotNull
  public String getPackageName() {
    return packageTextField.getText().trim();
  }

  @NotNull
  public String getClassName() {
    return classNameField.getText().trim();
  }

  protected JComponent createCenterPanel() {
    sourceMethodTextField.setEditable(false);

    final DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    };

    classNameField.getDocument().addDocumentListener(docListener);
    myFieldsCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        validateButtons();
      }
    });
    myInnerClassNameTextField.getDocument().addDocumentListener(docListener);

    final PsiFile file = sourceMethod.getContainingFile();
    if (file instanceof PsiJavaFile) {
      final String packageName = ((PsiJavaFile)file).getPackageName();
      packageTextField.setText(packageName);
    }

    final PsiClass containingClass = sourceMethod.getContainingClass();
    final String containingClassName = containingClass instanceof PsiAnonymousClass ? "Anonymous " + ((PsiAnonymousClass)containingClass).getBaseClassType().getClassName() : containingClass.getName();
    final String sourceMethodName = sourceMethod.getName();
    sourceMethodTextField.setText(containingClassName + '.' + sourceMethodName);
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(useExistingClassButton);
    buttonGroup.add(createNewClassButton);
    buttonGroup.add(myCreateInnerClassButton);
    createNewClassButton.setSelected(true);
    final ActionListener enableListener = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        toggleRadioEnablement();
      }
    };
    useExistingClassButton.addActionListener(enableListener);
    createNewClassButton.addActionListener(enableListener);
    myCreateInnerClassButton.addActionListener(enableListener);
    toggleRadioEnablement();
    
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    myFieldsCombo.setModel(model);
    myFieldsCombo.setRenderer(new DefaultListCellRenderer(){
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof PsiField) {
          final PsiField field = (PsiField)value;
          setText(field.getName());
          setIcon(field.getIcon(Iconable.ICON_FLAG_VISIBILITY));
        }
        return rendererComponent;
      }
    });
    existingClassField.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        final PsiClass currentClass = JavaPsiFacade.getInstance(myProject).findClass(existingClassField.getText(), GlobalSearchScope.allScope(myProject));
        if (currentClass != null) {
          model.removeAllElements();
          for (PsiField field : currentClass.getFields()) {
            final PsiType returnType = sourceMethod.getReturnType();
            assert returnType != null;
            if (TypeConversionUtil.isAssignable(field.getType(), returnType)) {
              model.addElement(field);
            }
          }
        }
      }
    });
    return myWholePanel;
  }

  private void toggleRadioEnablement() {
    UIUtil.setEnabled(myExistingClassPanel, useExistingClassButton.isSelected(), true);
    UIUtil.setEnabled(myNewClassPanel, createNewClassButton.isSelected(), true);
    UIUtil.setEnabled(myCreateInnerPanel, myCreateInnerClassButton.isSelected(), true);
    final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    if (useExistingClassButton.isSelected()) {
      focusManager.requestFocus(existingClassField, true);
    }
    else if (myCreateInnerClassButton.isSelected()) {
      focusManager.requestFocus(myInnerClassNameTextField, true);
    }
    else {
      focusManager.requestFocus(classNameField, true);
    }
    validateButtons();
  }


  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.WrapReturnValue);
  }

  private void createUIComponents() {
    final com.intellij.openapi.editor.event.DocumentAdapter adapter = new com.intellij.openapi.editor.event.DocumentAdapter() {
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        validateButtons();
      }
    };

    packageTextField =
      new PackageNameReferenceEditorCombo("", myProject, RECENT_KEYS, RefactoringBundle.message("choose.destination.package"));
    packageTextField.getChildComponent().getDocument().addDocumentListener(adapter);

    existingClassField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject())
          .createWithInnerClassesScopeChooser(RefactorJBundle.message("select.wrapper.class"), GlobalSearchScope.allScope(myProject), null, null);
        final String classText = existingClassField.getText();
        final PsiClass currentClass = JavaPsiFacade.getInstance(myProject).findClass(classText, GlobalSearchScope.allScope(myProject));
        if (currentClass != null) {
          chooser.selectClass(currentClass);
        }
        chooser.showDialog();
        final PsiClass selectedClass = chooser.getSelectedClass();
        if (selectedClass != null) {
          existingClassField.setText(selectedClass.getQualifiedName());
        }
      }
    }, "", PsiManager.getInstance(myProject), true, RECENT_KEYS);
    existingClassField.getChildComponent().getDocument().addDocumentListener(adapter);
  }
}

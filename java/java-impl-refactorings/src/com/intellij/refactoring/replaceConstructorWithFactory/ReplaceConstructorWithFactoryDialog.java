// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryDialog extends RefactoringDialog {
  private NameSuggestionsField myNameField;
  private final ReferenceEditorWithBrowseButton myTfTargetClassName;
  private JComboBox myTargetClassNameCombo;
  private final PsiClass myContainingClass;
  private final PsiMethod myConstructor;
  private final boolean myIsInner;
  private NameSuggestionsField.DataChanged myNameChangedListener;

  ReplaceConstructorWithFactoryDialog(Project project, PsiMethod constructor, PsiClass containingClass) {
    super(project, true);
    myContainingClass = containingClass;
    myConstructor = constructor;
    myIsInner = myContainingClass.getContainingClass() != null
                && !myContainingClass.hasModifierProperty(PsiModifier.STATIC);

    setTitle(ReplaceConstructorWithFactoryHandler.getRefactoringName());

    myTfTargetClassName = JavaReferenceEditorUtil.createReferenceEditorWithBrowseButton(new ChooseClassAction(), "", project, true);

    init();
  }

  @Override
  protected void dispose() {
    myNameField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  public String getName() {
    return myNameField.getEnteredName();
  }

  @Override
  protected String getHelpId() {
    return HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  private String getTargetClassName() {
    if (!myIsInner) {
      return myTfTargetClassName.getText();
    }
    else {
      return (String)myTargetClassNameCombo.getSelectedItem();
    }
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;

    gbc.insets = JBUI.insets(4, 0, 4, 8);
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    panel.add(new JLabel(JavaRefactoringBundle.message("factory.method.name.label")), gbc);

    gbc.gridx++;
    gbc.weightx = 1.0;
    @NonNls final String[] nameSuggestions = new String[]{
      "create" + myContainingClass.getName(),
      "new" + myContainingClass.getName(),
      "getInstance",
      "newInstance"
      };
    myNameField = new NameSuggestionsField(nameSuggestions, getProject());
    myNameChangedListener = () -> validateButtons();
    myNameField.addDataChangedListener(myNameChangedListener);
    panel.add(myNameField.getComponent(), gbc);

    JPanel targetClassPanel = createTargetPanel();

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    panel.add(targetClassPanel, gbc);

    return panel;
  }

  private JPanel createTargetPanel() {
    JPanel targetClassPanel = new JPanel(new BorderLayout());
    if (!myIsInner) {
      JLabel label = new JLabel(JavaRefactoringBundle.message("replace.constructor.with.factory.target.fq.name"));
      label.setLabelFor(myTfTargetClassName);
      targetClassPanel.add(label, BorderLayout.NORTH);
      targetClassPanel.add(myTfTargetClassName, BorderLayout.CENTER);
      myTfTargetClassName.setText(myContainingClass.getQualifiedName());
    }
    else {
      ArrayList<String> list = new ArrayList<>();
      PsiElement parent = myContainingClass;
      while (parent instanceof PsiClass) {
        list.add(((PsiClass)parent).getQualifiedName());
        parent = parent.getParent();
      }

      myTargetClassNameCombo = new JComboBox(ArrayUtilRt.toStringArray(list));
      JLabel label = new JLabel(JavaRefactoringBundle.message("replace.constructor.with.factory.target.fq.name"));
      label.setLabelFor(myTargetClassNameCombo.getEditor().getEditorComponent());
      targetClassPanel.add(label, BorderLayout.NORTH);
      targetClassPanel.add(myTargetClassNameCombo, BorderLayout.CENTER);
    }
    return targetClassPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryDialog";
  }

  private class ChooseClassAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject()).createProjectScopeChooser(
        RefactoringBundle.message("choose.destination.class"));
      chooser.selectDirectory(myContainingClass.getContainingFile().getContainingDirectory());
      chooser.showDialog();
      PsiClass aClass = chooser.getSelected();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected void doAction() {
    final Project project = getProject();
    final PsiManager manager = PsiManager.getInstance(project);
    final String targetClassName = getTargetClassName();
    final PsiClass targetClass =
      JavaPsiFacade.getInstance(manager.getProject()).findClass(targetClassName, GlobalSearchScope.allScope(project));
    if (targetClass == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("class.0.not.found", targetClassName));
      CommonRefactoringUtil.showErrorMessage(ReplaceConstructorWithFactoryHandler.getRefactoringName(),
                                             message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, targetClass)) return;

    invokeRefactoring(new ReplaceConstructorWithFactoryProcessor(project, myConstructor, myContainingClass,
                                                                 targetClass, getName()));
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final String name = myNameField.getEnteredName();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(myContainingClass.getProject());
    if (!nameHelper.isIdentifier(name)) {
      throw new ConfigurationException(JavaRefactoringBundle.message("replace.constructor.factory.error.invalid.factory.method.name", name));
    }
  }
}
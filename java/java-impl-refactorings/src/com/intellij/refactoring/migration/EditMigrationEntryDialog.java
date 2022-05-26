// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class EditMigrationEntryDialog extends DialogWrapper{
  private JRadioButton myRbPackage;
  private JRadioButton myRbClass;
  private EditorTextField myOldNameField;
  private EditorTextField myNewNameField;
  private final Project myProject;

  public EditMigrationEntryDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(JavaRefactoringBundle.message("edit.migration.entry.title"));
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myOldNameField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.insets = JBUI.insets(4);
    gbConstraints.weighty = 0;

    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 0;
    myRbPackage = new JRadioButton(JavaRefactoringBundle.message("migration.entry.package"));
    panel.add(myRbPackage, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 0;
    myRbClass = new JRadioButton(JavaRefactoringBundle.message("migration.entry.class"));
    panel.add(myRbClass, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    panel.add(new JPanel(), gbConstraints);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbPackage);
    buttonGroup.add(myRbClass);

    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.NONE;
    JLabel oldNamePrompt = new JLabel(JavaRefactoringBundle.message("migration.entry.old.name"));
    panel.add(oldNamePrompt, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    final LanguageTextField.DocumentCreator documentCreator = new LanguageTextField.DocumentCreator() {
      @Override
      public Document createDocument(String value, @Nullable Language language, Project project) {
        PsiPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
        final JavaCodeFragment fragment =
          JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment("", defaultPackage, true, true);
        return PsiDocumentManager.getInstance(project).getDocument(fragment);
      }
    };
    myOldNameField = new LanguageTextField(JavaLanguage.INSTANCE, myProject, "", documentCreator);
    FontMetrics metrics = myOldNameField.getFontMetrics(myOldNameField.getFont());
    int columnWidth = metrics.charWidth('m');
    myOldNameField.setPreferredWidth(25 * columnWidth);
    panel.add(myOldNameField, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.fill = GridBagConstraints.NONE;
    JLabel newNamePrompt = new JLabel(JavaRefactoringBundle.message("migration.entry.new.name"));
    panel.add(newNamePrompt, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    myNewNameField = new LanguageTextField(JavaLanguage.INSTANCE, myProject, "", documentCreator);
    panel.add(myNewNameField, gbConstraints);

    final DocumentListener documentAdapter = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        validateOKButton();
      }
    };
    myOldNameField.getDocument().addDocumentListener(documentAdapter);
    myNewNameField.getDocument().addDocumentListener(documentAdapter);
    return panel;
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    String text = myOldNameField.getText();
    text = text.trim();
    PsiManager manager = PsiManager.getInstance(myProject);
    if (!PsiNameHelper.getInstance(manager.getProject()).isQualifiedName(text)){
      isEnabled = false;
    }
    text = myNewNameField.getText();
    text = text.trim();
    if (!PsiNameHelper.getInstance(manager.getProject()).isQualifiedName(text)){
      isEnabled = false;
    }
    setOKActionEnabled(isEnabled);
  }

  public void setEntry(MigrationMapEntry entry) {
    myOldNameField.setText(entry.getOldName());
    myNewNameField.setText(entry.getNewName());
    myRbPackage.setSelected(entry.getType() == MigrationMapEntry.PACKAGE);
    myRbClass.setSelected(entry.getType() == MigrationMapEntry.CLASS);
    validateOKButton();
  }

  public void updateEntry(MigrationMapEntry entry) {
    entry.setOldName(myOldNameField.getText().trim());
    entry.setNewName(myNewNameField.getText().trim());
    if (myRbPackage.isSelected()){
      entry.setType(MigrationMapEntry.PACKAGE);
      entry.setRecursive(true);
    }
    else{
      entry.setType(MigrationMapEntry.CLASS);
    }
  }
}

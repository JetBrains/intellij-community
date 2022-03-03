// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class CreateFieldFromParameterDialog extends DialogWrapper {
  private final Project myProject;
  private final String[] myNames;
  private final PsiType[] myTypes;
  private final PsiClass myTargetClass;
  private final boolean myFieldMayBeFinal;

  private JComponent myNameField;
  private JCheckBox myCbFinal;
  private static final @NonNls String PROPERTY_NAME = "CREATE_FIELD_FROM_PARAMETER_DECLARE_FINAL";
  private TypeSelector myTypeSelector;

  public CreateFieldFromParameterDialog(@NotNull Project project,
                                        String @NotNull [] names,
                                        @NotNull PsiClass targetClass,
                                        boolean fieldMayBeFinal,
                                        PsiType @NotNull ... types) {
    super(project, true);
    myProject = project;
    myNames = names;
    myTypes = types;
    myTargetClass = targetClass;
    myFieldMayBeFinal = fieldMayBeFinal;

    setTitle(JavaBundle.message("dialog.create.field.from.parameter.title"));

    init();
  }

  @Override
  protected void doOKAction() {
    if (myCbFinal.isEnabled()) {
      PropertiesComponent.getInstance().setValue(PROPERTY_NAME, myCbFinal.isSelected());
    }

    final PsiField[] fields = myTargetClass.getFields();
    for (PsiField field : fields) {
      if (field.getName().equals(getEnteredName())) {
        int result = Messages.showOkCancelDialog(
          getContentPane(),
          JavaBundle.message("dialog.create.field.from.parameter.already.exists.text", getEnteredName()),
          JavaBundle.message("dialog.create.field.from.parameter.already.exists.title"),
          JavaBundle.message("dialog.create.field.from.parameter.already.exists.use.existing.button"),
          Messages.getCancelButton(),
          Messages.getQuestionIcon());
        if (result == Messages.OK) {
          close(OK_EXIT_CODE);
        }
        else {
          return;
        }
      }
    }

    close(OK_EXIT_CODE);
  }

  @Override
  protected void init() {
    super.init();
    updateOkStatus();
  }

  public String getEnteredName() {
    if (myNameField instanceof JComboBox) {
      JComboBox combobox = (JComboBox)myNameField;
      return (String)combobox.getEditor().getItem();
    }
    return ((JTextField)myNameField).getText();
  }

  public boolean isDeclareFinal() {
    if (myCbFinal.isEnabled()) {
      return myCbFinal.isSelected();
    }

    return false;
  }

  @Override
  protected JComponent createNorthPanel() {
    if (myNames.length > 1) {
      final ComboBox combobox = new ComboBox(myNames, 200);
      myNameField = combobox;
      combobox.setEditable(true);
      combobox.setSelectedIndex(0);
      combobox.setMaximumRowCount(8);

      combobox.registerKeyboardAction(
        __ -> {
          if (combobox.isPopupVisible()) {
            combobox.setPopupVisible(false);
          }
          else {
            doCancelAction();
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );

      combobox.addItemListener(__ -> updateOkStatus());
      combobox.getEditor().getEditorComponent().addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            updateOkStatus();
          }

          @Override
          public void keyReleased(KeyEvent e) {
            updateOkStatus();
          }

          @Override
          public void keyTyped(KeyEvent e) {
            updateOkStatus();
          }
        }
      );
    }
    else {
      JTextField field = new JTextField() {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          return new Dimension(200, size.height);
        }
      };
      myNameField = field;
      field.setText(myNames[0]);

      field.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          updateOkStatus();
        }
      });
    }

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insets(4);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    final JLabel typeLabel = new JLabel(JavaBundle.message("dialog.create.field.from.parameter.field.type.label"));
    panel.add(typeLabel, gbConstraints);
    gbConstraints.gridx = 1;
    if (myTypes.length > 1) {
      myTypeSelector = new TypeSelector(myProject);
      myTypeSelector.setTypes(myTypes);
    }
    else {
      myTypeSelector = new TypeSelector(myTypes[0], myProject);
    }
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    JLabel namePrompt = new JLabel(JavaBundle.message("dialog.create.field.from.parameter.field.name.label"));
    panel.add(namePrompt, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    panel.add(myNameField, gbConstraints);

    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = JBInsets.emptyInsets();

    myCbFinal = new JCheckBox(JavaBundle.message("dialog.create.field.from.parameter.declare.final.checkbox"));
    if (myFieldMayBeFinal) {
      myCbFinal.setSelected(PropertiesComponent.getInstance().isTrueValue(PROPERTY_NAME));
    }
    else {
      myCbFinal.setSelected(false);
      myCbFinal.setEnabled(false);
    }

    gbConstraints.gridy++;
    panel.add(myCbFinal, gbConstraints);
    myCbFinal.addActionListener(__ -> requestFocusInNameWindow());

    return panel;
  }

  private void requestFocusInNameWindow() {
    if (myNameField instanceof JTextField) {
      myNameField.requestFocusInWindow();
    }
    else {
      ((JComboBox<?>)myNameField).getEditor().getEditorComponent().requestFocusInWindow();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(text));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Nullable
  public PsiType getType() {
    return myTypeSelector.getSelectedType();
  }
}

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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;

class CreateFieldFromParameterDialog extends DialogWrapper {
  private final Project myProject;
  private final String[] myNames;
  private final PsiType[] myTypes;
  private final PsiClass myTargetClass;
  private final boolean myFieldMayBeFinal;

  private JComponent myNameField;
  private JCheckBox myCbFinal;
  private static final @NonNls String PROPERTY_NAME = "CREATE_FIELD_FROM_PARAMETER_DECLARE_FINAL";
  private TypeSelector myTypeSelector;

  public CreateFieldFromParameterDialog(Project project,
                                        String[] names,
                                        PsiClass targetClass,
                                        final boolean fieldMayBeFinal, PsiType... types) {
    super(project, true);
    myProject = project;
    myNames = names;
    myTypes = types;
    myTargetClass = targetClass;
    myFieldMayBeFinal = fieldMayBeFinal;

    setTitle(CodeInsightBundle.message("dialog.create.field.from.parameter.title"));

    init();
  }

  protected void doOKAction() {
    if (myCbFinal.isEnabled()) {
      PropertiesComponent.getInstance().setValue(PROPERTY_NAME, ""+myCbFinal.isSelected());
    }

    final PsiField[] fields = myTargetClass.getFields();
    for (PsiField field : fields) {
      if (field.getName().equals(getEnteredName())) {
        int result = Messages.showOkCancelDialog(
          getContentPane(),
          CodeInsightBundle.message("dialog.create.field.from.parameter.already.exists.text", getEnteredName()),
          CodeInsightBundle.message("dialog.create.field.from.parameter.already.exists.title"),
          Messages.getQuestionIcon());
        if (result == 0) {
          close(OK_EXIT_CODE);
        }
        else {
          return;
        }
      }
    }

    close(OK_EXIT_CODE);
  }

  protected void init() {
    super.init();
    updateOkStatus();
  }

  public String getEnteredName() {
    if (myNameField instanceof JComboBox) {
      JComboBox combobox = (JComboBox) myNameField;
      return (String) combobox.getEditor().getItem();
    }
    else {
      return ((JTextField) myNameField).getText();
    }
  }

  public boolean isDeclareFinal() {
    if (myCbFinal.isEnabled()) {
      return myCbFinal.isSelected();
    }

    return false;
  }

  protected JComponent createNorthPanel() {
    if (myNames.length > 1) {
      final ComboBox combobox = new ComboBox(myNames, 200);
      myNameField = combobox;
      combobox.setEditable(true);
      combobox.setSelectedIndex(0);
      combobox.setMaximumRowCount(8);

      combobox.registerKeyboardAction(
          new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              if (combobox.isPopupVisible()) {
                combobox.setPopupVisible(false);
              }
              else {
                doCancelAction();
              }
            }
          },
          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );

      combobox.addItemListener(
          new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
              updateOkStatus();
            }
          }
      );
      combobox.getEditor().getEditorComponent().addKeyListener(
          new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
              updateOkStatus();
            }

            public void keyReleased(KeyEvent e) {
              updateOkStatus();
            }

            public void keyTyped(KeyEvent e) {
              updateOkStatus();
            }
          }
      );
    }
    else {
      JTextField field = new JTextField() {
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          return new Dimension(200, size.height);
        }
      };
      myNameField = field;
      field.setText(myNames[0]);

      field.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          updateOkStatus();
        }
      });
    }

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    final JLabel typeLabel = new JLabel(CodeInsightBundle.message("dialog.create.field.from.parameter.field.type.label"));
    panel.add(typeLabel, gbConstraints);
    gbConstraints.gridx = 1;
    if (myTypes.length > 1) {
      myTypeSelector = new TypeSelector();
      myTypeSelector.setTypes(myTypes);
    } else {
      myTypeSelector = new TypeSelector(myTypes[0]);
    }
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    JLabel namePrompt = new JLabel(CodeInsightBundle.message("dialog.create.field.from.parameter.field.name.label"));
    panel.add(namePrompt, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    panel.add(myNameField, gbConstraints);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = new Insets(0, 0, 0, 0);

    myCbFinal = new JCheckBox(CodeInsightBundle.message("dialog.create.field.from.parameter.declare.final.checkbox"));
    if (myFieldMayBeFinal) {
      myCbFinal.setSelected(PropertiesComponent.getInstance().isTrueValue(PROPERTY_NAME));
    } else {
      myCbFinal.setSelected(false);
      myCbFinal.setEnabled(false);
    }

    gbConstraints.gridy++;
    panel.add(myCbFinal, gbConstraints);
    myCbFinal.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        requestFocusInNameWindow();
        if (myCbFinal.isEnabled()) {
        }
      }
    });

    return panel;
  }

  private void requestFocusInNameWindow() {
    if (myNameField instanceof JTextField) {
      myNameField.requestFocusInWindow();
    }
    else {
      ((JComboBox) myNameField).getEditor().getEditorComponent().requestFocusInWindow();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(text));
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Nullable
  public PsiType getType() {
    return myTypeSelector.getSelectedType();
  }
}

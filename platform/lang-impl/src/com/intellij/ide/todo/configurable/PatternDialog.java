/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.todo.configurable;

import com.intellij.application.options.colors.ColorAndFontDescription;
import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class PatternDialog extends DialogWrapper {
  private final TodoPattern myPattern;

  private final ComboBox<Icon> myIconComboBox;
  private final JBCheckBox myCaseSensitiveCheckBox;
  private final JBTextField myPatternStringField;
  private final ColorAndFontDescriptionPanel myColorAndFontDescriptionPanel;
  private final ColorAndFontDescription myColorAndFontDescription;
  private final JBCheckBox myUsedDefaultColorsCheckBox;

  public PatternDialog(Component parent, TodoPattern pattern) {
    super(parent, true);
    setTitle(IdeBundle.message("title.add.todo.pattern"));
    setResizable(false);

    final TodoAttributes attrs = pattern.getAttributes();
    myPattern = pattern;
    myIconComboBox = new ComboBox<>(new Icon[]{AllIcons.General.TodoDefault, AllIcons.General.TodoQuestion,
      AllIcons.General.TodoImportant});
    myIconComboBox.setSelectedItem(attrs.getIcon());
    myIconComboBox.setRenderer(new ListCellRendererWrapper<Icon>() {
      @Override
      public void customize(JList list, Icon value, int index, boolean selected, boolean hasFocus) {
        setIcon(value);
        setText(" ");
      }
    });
    myCaseSensitiveCheckBox = new JBCheckBox(IdeBundle.message("checkbox.case.sensitive"), pattern.isCaseSensitive());
    myPatternStringField = new JBTextField(pattern.getPatternString());

    // use default colors check box
    myUsedDefaultColorsCheckBox = new JBCheckBox(IdeBundle.message("checkbox.todo.use.default.colors"));
    myUsedDefaultColorsCheckBox.setSelected(!attrs.shouldUseCustomTodoColor());

    myColorAndFontDescriptionPanel = new ColorAndFontDescriptionPanel();

    TextAttributes attributes = myPattern.getAttributes().getCustomizedTextAttributes();
    myColorAndFontDescription = new TextAttributesDescription("null", null, attributes, null,
                                                              EditorColorsManager.getInstance().getGlobalScheme(), null, null) {
      @Override
      public void apply(EditorColorsScheme scheme) {
      }

      @Override
      public boolean isErrorStripeEnabled() {
        return true;
      }

      @Override
      public boolean isEditable() {
        return true;
      }
    };
    myColorAndFontDescriptionPanel.reset(myColorAndFontDescription);

    updateCustomColorsPanel();
    myUsedDefaultColorsCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateCustomColorsPanel();
      }
    });
    init();
  }

  private void updateCustomColorsPanel() {
    if (useCustomTodoColor()) {
      // restore controls
      myColorAndFontDescriptionPanel.reset(myColorAndFontDescription);
    }
    else {
      // disable controls
      myColorAndFontDescriptionPanel.resetDefault();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPatternStringField;
  }

  @Override
  protected void doOKAction() {
    myPattern.setPatternString(myPatternStringField.getText().trim());
    myPattern.setCaseSensitive(myCaseSensitiveCheckBox.isSelected());

    final TodoAttributes attrs = myPattern.getAttributes();
    attrs.setIcon((Icon)myIconComboBox.getSelectedItem());
    attrs.setUseCustomTodoColor(useCustomTodoColor(), TodoAttributesUtil.getDefaultColorSchemeTextAttributes());

    if (useCustomTodoColor()) {
      myColorAndFontDescriptionPanel.apply(myColorAndFontDescription, null);
    }
    super.doOKAction();
  }


  private boolean useCustomTodoColor() {
    return !myUsedDefaultColorsCheckBox.isSelected();
  }

  @Override
  protected JComponent createCenterPanel() {
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(IdeBundle.message("label.todo.pattern"), myPatternStringField)
      .addLabeledComponent(IdeBundle.message("label.todo.icon"), myIconComboBox)
      .addComponent(myCaseSensitiveCheckBox)
      .addComponent(myUsedDefaultColorsCheckBox)
      .addComponent(myColorAndFontDescriptionPanel)
      .getPanel();
  }
}

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

package com.intellij.ide.todo.configurable;

import com.intellij.application.options.colors.ColorAndFontDescription;
import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author Vladimir Kondratyev
 */
class PatternDialog extends DialogWrapper{
  private final TodoPattern myPattern;

  private final JComboBox myIconComboBox;
  private final JCheckBox myCaseSensitiveCheckBox;
  private final JTextField myPatternStringField;
  private final ColorAndFontDescriptionPanel myColorAndFontDescriptionPanel;
  private final ColorAndFontDescription myColorAndFontDescription;
  private final JCheckBox myUsedDefaultColorsCeckBox;

  public PatternDialog(Component parent, TodoPattern pattern){
    super(parent, true);

    final TodoAttributes attrs = pattern.getAttributes();
    myPattern=pattern;
    myIconComboBox=new JComboBox(
      new Icon[]{TodoAttributes.DEFAULT_ICON,TodoAttributes.QUESTION_ICON,TodoAttributes.IMPORTANT_ICON}
    );
    myIconComboBox.setSelectedItem(attrs.getIcon());
    myIconComboBox.setRenderer(new TodoTypeListCellRenderer());
    myCaseSensitiveCheckBox=new JCheckBox(IdeBundle.message("checkbox.case.sensitive"),pattern.isCaseSensitive());
    myPatternStringField=new JTextField(pattern.getPatternString());


    // use default colors check box
    myUsedDefaultColorsCeckBox = new JCheckBox(IdeBundle.message("checkbox.todo.use.default.colors"));
    myUsedDefaultColorsCeckBox.setSelected(!attrs.shouldUseCustomTodoColor());

    myColorAndFontDescriptionPanel = new ColorAndFontDescriptionPanel();

    TextAttributes attributes = myPattern.getAttributes().getCustomizedTextAttributes();

    myColorAndFontDescription = new TextAttributesDescription(null, null, attributes, null, EditorColorsManager.getInstance().getGlobalScheme(),
                                                              null, null) {
      public void apply(EditorColorsScheme scheme) {

      }

      public boolean isErrorStripeEnabled() {
        return true;
      }
    };

    myColorAndFontDescriptionPanel.reset(myColorAndFontDescription);

    updateCustomColorsPanel();
    myUsedDefaultColorsCeckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateCustomColorsPanel();
      }
    });

    init();
  }

  private void updateCustomColorsPanel() {
    final boolean useCustomColors = useCustomTodoColor();

    if (useCustomColors) {
      // restore controls
      myColorAndFontDescriptionPanel.reset(myColorAndFontDescription);
    } else {
      // disable controls
      myColorAndFontDescriptionPanel.resetDefault();
    }
  }

  public JComponent getPreferredFocusedComponent(){
    return myPatternStringField;
  }

  protected void doOKAction(){
    myPattern.setPatternString(myPatternStringField.getText().trim());
    myPattern.setCaseSensitive(myCaseSensitiveCheckBox.isSelected());

    final TodoAttributes attrs = myPattern.getAttributes();
    attrs.setIcon((Icon)myIconComboBox.getSelectedItem());
    attrs.setUseCustomTodoColor(useCustomTodoColor());

    if (useCustomTodoColor()) {
      myColorAndFontDescriptionPanel.apply(myColorAndFontDescription, null);
    }
    super.doOKAction();
  }


  private boolean useCustomTodoColor() {
    return !myUsedDefaultColorsCeckBox.isSelected();
  }

  protected JComponent createCenterPanel(){
    JPanel panel=new JPanel(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,10),0,0);

    JLabel patternLabel=new JLabel(IdeBundle.message("label.todo.pattern"));
    panel.add(patternLabel, gb);
    Dimension oldPreferredSize=myPatternStringField.getPreferredSize();
    myPatternStringField.setPreferredSize(new Dimension(300,oldPreferredSize.height));
    gb.gridx = 1;
    gb.gridwidth = GridBagConstraints.REMAINDER;
    gb.weightx = 1;
    panel.add(myPatternStringField,gb);

    JLabel iconLabel=new JLabel(IdeBundle.message("label.todo.icon"));
    gb.gridy++;
    gb.gridx = 0;
    gb.gridwidth = 1;
    gb.weightx = 0;
    panel.add(iconLabel, gb);

    gb.gridx = 1;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = GridBagConstraints.REMAINDER;
    gb.weightx = 0;
    panel.add(myIconComboBox, gb);

    gb.gridy++;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.gridwidth = GridBagConstraints.REMAINDER;
    gb.weightx = 1;
    panel.add(myCaseSensitiveCheckBox, gb);

    gb.gridy++;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.gridwidth = GridBagConstraints.REMAINDER;
    gb.weightx = 1;
    panel.add(myUsedDefaultColorsCeckBox, gb);

    gb.gridy++;
    gb.gridx = 0;
    gb.gridwidth = GridBagConstraints.REMAINDER;
    gb.weightx = 1;
    panel.add(myColorAndFontDescriptionPanel, gb);
    return panel;
  }
}

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
package com.intellij.application.options;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class CodeStyleGenerationConfigurable implements Configurable {
  JPanel myPanel;
  private JTextField myFieldPrefixField;
  private JTextField myStaticFieldPrefixField;
  private JTextField myParameterPrefixField;
  private JTextField myLocalVariablePrefixField;

  private JTextField myFieldSuffixField;
  private JTextField myStaticFieldSuffixField;
  private JTextField myParameterSuffixField;
  private JTextField myLocalVariableSuffixField;

  private JCheckBox myCbPreferLongerNames;
  private JCheckBox myCbLineCommentAtFirstColumn;
  private JCheckBox myCbBlockCommentAtFirstColumn;


  private final MembersOrderList myMembersOrderList;
  private JBScrollPane myMembersListScroll;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;

  private final CodeStyleSettings mySettings;
  private JCheckBox myCbGenerateFinalParameters;
  private JCheckBox myCbGenerateFinalLocals;
  private JCheckBox myCbUseExternalAnnotations;
  private JCheckBox myInsertOverrideAnnotationCheckBox;

  public CodeStyleGenerationConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
    myMembersOrderList = new MembersOrderList();
  }

  public JComponent createComponent() {
    myMembersListScroll.getViewport().add(myMembersOrderList);

    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsUp(myMembersOrderList);
      }
    });

    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsDown(myMembersOrderList);
      }
    });
    return myPanel;
  }

  public void disposeUIResources() {
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.code.generation");
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle.codegen";
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }


  /*private JPanel createNamingPanel() {
    OptionGroup optionGroup = new OptionGroup("Naming");

    myCbPreferLongerNames = new JCheckBox("Prefer longer names");

    optionGroup.add(myCbPreferLongerNames);

    optionGroup.add(new JLabel("Name prefix for:"));

    myFieldPrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Field"), myFieldPrefixField, true);

    myStaticFieldPrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Static field"), myStaticFieldPrefixField, true);

    myParameterPrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Parameter"), myParameterPrefixField, true);

    myLocalVariablePrefixField = new JTextField(8);
    optionGroup.add(new JLabel("Local variable"), myLocalVariablePrefixField, true);

    optionGroup.add(new JLabel("Name suffix for:"));

    myFieldSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Field"), myFieldSuffixField, true);

    myStaticFieldSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Static field"), myStaticFieldSuffixField, true);

    myParameterSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Parameter"), myParameterSuffixField, true);

    myLocalVariableSuffixField = new JTextField(8);
    optionGroup.add(new JLabel("Local variable"), myLocalVariableSuffixField, true);

    return optionGroup.createPanel();
  }

  private JPanel createCommentPanel() {
    OptionGroup optionGroup = new OptionGroup("Comment Code");

    myCbLineCommentAtFirstColumn = new JCheckBox("Line comment at first column");
    optionGroup.add(myCbLineCommentAtFirstColumn);

    myCbBlockCommentAtFirstColumn = new JCheckBox("Block comment at first column");
    optionGroup.add(myCbBlockCommentAtFirstColumn);

    return optionGroup.createPanel();
  }

  private JPanel createRightMarginPanel() {
    OptionGroup optionGroup = new OptionGroup("Wrapping ");

    myRightMarginField = new JTextField(4);
    optionGroup.add(new JLabel("Right margin (columns)") ,myRightMarginField);

    return optionGroup.createPanel();
  }

  private JPanel createLineSeparatorPanel(){
    OptionGroup optionGroup = new OptionGroup("Line Separator (for new files) ");


    myLineSeparatorCombo = new JComboBox();
    myLineSeparatorCombo.addItem(SYSTEM_DEPENDANT_STRING);
    myLineSeparatorCombo.addItem(UNIX_STRING);
    myLineSeparatorCombo.addItem(WINDOWS_STRING);
    myLineSeparatorCombo.addItem(MACINTOSH_STRING);

    optionGroup.add(myLineSeparatorCombo);

    return optionGroup.createPanel();
  }

  private JPanel createKeepWhenReformattingPanel() {
    OptionGroup optionGroup = new OptionGroup("Keep When Reformatting");

    myCbKeepLineBreaks = new JCheckBox("Line breaks");
    optionGroup.add(myCbKeepLineBreaks);

    myCbKeepFirstColumnComment = new JCheckBox("Comment at first column");
    optionGroup.add(myCbKeepFirstColumnComment);

    myCbKeepControlStatementInOneLine = new JCheckBox("Control statement in one line");
    optionGroup.add(myCbKeepControlStatementInOneLine);

    return optionGroup.createPanel();
  }

  private JPanel createMembersOrderPanel() {

    OptionGroup optionGroup = new OptionGroup("Order of Members");

    JPanel panel = new JPanel(new GridBagLayout());

    myMembersOrderList = new MembersOrderList();
    panel.add(new JScrollPane(myMembersOrderList), new GridBagConstraints(0,0,1,2,1,1,GridBagConstraints.NORTH,GridBagConstraints.BOTH,new Insets(0,0,0,0), 0,0));

    JButton moveUpButton = new JButton("Move Up");

    moveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e){
        ListUtil.moveSelectedItemsUp(myMembersOrderList);
      }
    });
    panel.add(moveUpButton, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.NORTH,GridBagConstraints.HORIZONTAL,new Insets(0,5,5,0), 0,0));

   JButton movDownButton = new JButton("Move Down");
    moveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e){
        ListUtil.moveSelectedItemsDown(myMembersOrderList);
      }
    });
   panel.add(movDownButton, new GridBagConstraints(1,1,1,1,0,1,GridBagConstraints.NORTH,GridBagConstraints.HORIZONTAL,new Insets(0,5,5,0), 0,0));

    optionGroup.add(panel);

    return optionGroup.createPanel();
  }*/

  public void reset() {
    myCbPreferLongerNames.setSelected(mySettings.PREFER_LONGER_NAMES);

    myFieldPrefixField.setText(mySettings.FIELD_NAME_PREFIX);
    myStaticFieldPrefixField.setText(mySettings.STATIC_FIELD_NAME_PREFIX);
    myParameterPrefixField.setText(mySettings.PARAMETER_NAME_PREFIX);
    myLocalVariablePrefixField.setText(mySettings.LOCAL_VARIABLE_NAME_PREFIX);

    myFieldSuffixField.setText(mySettings.FIELD_NAME_SUFFIX);
    myStaticFieldSuffixField.setText(mySettings.STATIC_FIELD_NAME_SUFFIX);
    myParameterSuffixField.setText(mySettings.PARAMETER_NAME_SUFFIX);
    myLocalVariableSuffixField.setText(mySettings.LOCAL_VARIABLE_NAME_SUFFIX);

    myCbLineCommentAtFirstColumn.setSelected(mySettings.LINE_COMMENT_AT_FIRST_COLUMN);
    myCbBlockCommentAtFirstColumn.setSelected(mySettings.BLOCK_COMMENT_AT_FIRST_COLUMN);

    myCbGenerateFinalLocals.setSelected(mySettings.GENERATE_FINAL_LOCALS);
    myCbGenerateFinalParameters.setSelected(mySettings.GENERATE_FINAL_PARAMETERS);
    myMembersOrderList.reset(mySettings);

    myCbUseExternalAnnotations.setSelected(mySettings.USE_EXTERNAL_ANNOTATIONS);
    myInsertOverrideAnnotationCheckBox.setSelected(mySettings.INSERT_OVERRIDE_ANNOTATION);
  }

  public void apply() {
    mySettings.PREFER_LONGER_NAMES = myCbPreferLongerNames.isSelected();

    mySettings.FIELD_NAME_PREFIX = myFieldPrefixField.getText().trim();
    mySettings.STATIC_FIELD_NAME_PREFIX = myStaticFieldPrefixField.getText().trim();
    mySettings.PARAMETER_NAME_PREFIX = myParameterPrefixField.getText().trim();
    mySettings.LOCAL_VARIABLE_NAME_PREFIX = myLocalVariablePrefixField.getText().trim();

    mySettings.FIELD_NAME_SUFFIX = myFieldSuffixField.getText().trim();
    mySettings.STATIC_FIELD_NAME_SUFFIX = myStaticFieldSuffixField.getText().trim();
    mySettings.PARAMETER_NAME_SUFFIX = myParameterSuffixField.getText().trim();
    mySettings.LOCAL_VARIABLE_NAME_SUFFIX = myLocalVariableSuffixField.getText().trim();

    mySettings.LINE_COMMENT_AT_FIRST_COLUMN = myCbLineCommentAtFirstColumn.isSelected();
    mySettings.BLOCK_COMMENT_AT_FIRST_COLUMN = myCbBlockCommentAtFirstColumn.isSelected();

    mySettings.GENERATE_FINAL_LOCALS = myCbGenerateFinalLocals.isSelected();
    mySettings.GENERATE_FINAL_PARAMETERS = myCbGenerateFinalParameters.isSelected();

    mySettings.USE_EXTERNAL_ANNOTATIONS = myCbUseExternalAnnotations.isSelected();
    mySettings.INSERT_OVERRIDE_ANNOTATION = myInsertOverrideAnnotationCheckBox.isSelected();

    myMembersOrderList.apply(mySettings);

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  public boolean isModified() {
    boolean isModified = isModified(myCbPreferLongerNames, mySettings.PREFER_LONGER_NAMES);

    isModified |= isModified(myFieldPrefixField, mySettings.FIELD_NAME_PREFIX);
    isModified |= isModified(myStaticFieldPrefixField, mySettings.STATIC_FIELD_NAME_PREFIX);
    isModified |= isModified(myParameterPrefixField, mySettings.PARAMETER_NAME_PREFIX);
    isModified |= isModified(myLocalVariablePrefixField, mySettings.LOCAL_VARIABLE_NAME_PREFIX);

    isModified |= isModified(myFieldSuffixField, mySettings.FIELD_NAME_SUFFIX);
    isModified |= isModified(myStaticFieldSuffixField, mySettings.STATIC_FIELD_NAME_SUFFIX);
    isModified |= isModified(myParameterSuffixField, mySettings.PARAMETER_NAME_SUFFIX);
    isModified |= isModified(myLocalVariableSuffixField, mySettings.LOCAL_VARIABLE_NAME_SUFFIX);

    isModified |= isModified(myCbLineCommentAtFirstColumn, mySettings.LINE_COMMENT_AT_FIRST_COLUMN);
    isModified |= isModified(myCbBlockCommentAtFirstColumn, mySettings.BLOCK_COMMENT_AT_FIRST_COLUMN);


    isModified |= isModified(myCbGenerateFinalLocals, mySettings.GENERATE_FINAL_LOCALS);
    isModified |= isModified(myCbGenerateFinalParameters, mySettings.GENERATE_FINAL_PARAMETERS);

    isModified |= isModified(myCbUseExternalAnnotations, mySettings.USE_EXTERNAL_ANNOTATIONS);
    isModified |= isModified(myInsertOverrideAnnotationCheckBox, mySettings.INSERT_OVERRIDE_ANNOTATION);

    isModified |= myMembersOrderList.isModified(mySettings);

    return isModified;
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, String value) {
    return !textField.getText().trim().equals(value);
  }

  private static class MembersOrderList extends JBList {

    private static abstract class PropertyManager {

      public final String myName;

      protected PropertyManager(String nameKey) {
        myName = ApplicationBundle.message(nameKey);
      }

      abstract void apply(CodeStyleSettings settings, int value);
      abstract int getValue(CodeStyleSettings settings);
    }

    private static final Map<String, PropertyManager> PROPERTIES = new HashMap<String, PropertyManager>();
    static {
      init();
    }

    private final DefaultListModel myModel;

    public MembersOrderList() {
      myModel = new DefaultListModel();
      setModel(myModel);
      setVisibleRowCount(PROPERTIES.size());
    }

    public void reset(final CodeStyleSettings settings) {
      myModel.removeAllElements();
      for (String string : getPropertyNames(settings)) {
        myModel.addElement(string);
      }

      setSelectedIndex(0);
    }

    private static void init() {
      PropertyManager staticFieldManager = new PropertyManager("listbox.members.order.static.fields") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.STATIC_FIELDS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.STATIC_FIELDS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(staticFieldManager.myName, staticFieldManager);

      PropertyManager instanceFieldManager = new PropertyManager("listbox.members.order.fields") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.FIELDS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.FIELDS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(instanceFieldManager.myName, instanceFieldManager);

      PropertyManager constructorManager = new PropertyManager("listbox.members.order.constructors") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.CONSTRUCTORS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.CONSTRUCTORS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(constructorManager.myName, constructorManager);

      PropertyManager staticMethodManager = new PropertyManager("listbox.members.order.static.methods") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.STATIC_METHODS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.STATIC_METHODS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(staticMethodManager.myName, staticMethodManager);

      PropertyManager instanceMethodManager = new PropertyManager("listbox.members.order.methods") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.METHODS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.METHODS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(instanceMethodManager.myName, instanceMethodManager);

      PropertyManager staticInnerClassManager = new PropertyManager("listbox.members.order.inner.static.classes") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.STATIC_INNER_CLASSES_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.STATIC_INNER_CLASSES_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(staticInnerClassManager.myName, staticInnerClassManager);

      PropertyManager innerClassManager = new PropertyManager("listbox.members.order.inner.classes") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.INNER_CLASSES_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.INNER_CLASSES_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(innerClassManager.myName, innerClassManager);
    }

    private static Iterable<String> getPropertyNames(final CodeStyleSettings settings) {
      List<String> result = new ArrayList<String>(PROPERTIES.keySet());
      Collections.sort(result, new Comparator<String>() {
        public int compare(String o1, String o2) {
          int weight1 = getWeight(o1);
          int weight2 = getWeight(o2);
          return weight1 - weight2;
        }

        private int getWeight(String o) {
          PropertyManager propertyManager = PROPERTIES.get(o);
          if (propertyManager == null) {
            throw new IllegalArgumentException("unexpected " + o);
          }
          return propertyManager.getValue(settings);
        }
      });
      return result;
    }

    public void apply(CodeStyleSettings settings) {
      for (int i = 0; i < myModel.size(); i++) {
        Object o = myModel.getElementAt(i);
        if (o == null) {
          throw new IllegalArgumentException("unexpected " + o);
        }
        PropertyManager propertyManager = PROPERTIES.get(o.toString());
        if (propertyManager == null) {
          throw new IllegalArgumentException("unexpected " + o);
        }
        propertyManager.apply(settings, i + 1);
      }
    }

    public boolean isModified(CodeStyleSettings settings) {
      Iterable<String> oldProperties = getPropertyNames(settings);
      int i = 0;
      for (String property : oldProperties) {
        if (i >= myModel.size() || !property.equals(myModel.getElementAt(i))) {
          return true;
        }
        i++;
      }
      return false;
    }
  }
}

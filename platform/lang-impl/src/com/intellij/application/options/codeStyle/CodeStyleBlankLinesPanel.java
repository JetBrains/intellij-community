/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.ui.OptionGroup;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

public class CodeStyleBlankLinesPanel extends MultilanguageCodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.codeStyle.CodeStyleBlankLinesPanel");

  private List<IntOption> myOptions = new ArrayList<IntOption>();
  private Set<String> myAllowedOptions = new HashSet<String>();
  private boolean myAllOptionsAllowed = false;
  private boolean myIsFirstUpdate = true;
  private final Map<String, String> myRenamedFields = new THashMap<String, String>();

  private MultiMap<String, Trinity<Class<? extends CustomCodeStyleSettings>, String, String>> myCustomOptions
    = new MultiMap<String, Trinity<Class<? extends CustomCodeStyleSettings>, String, String>>();

  private final JPanel myPanel = new JPanel(new GridBagLayout());

  public CodeStyleBlankLinesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  protected void init() {
    super.init();

    myPanel
      .add(createKeepBlankLinesPanel(),
           new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));
    myPanel
      .add(createBlankLinesPanel(),
           new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel
      .add(previewPanel,
           new GridBagConstraints(1, 0, 1, 2, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 4), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

    myIsFirstUpdate = false;
  }

  @Override
  protected LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.BLANK_LINES_SETTINGS;
  }

  private JPanel createBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup(BLANK_LINES);

    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.before.package.statement"), "BLANK_LINES_BEFORE_PACKAGE");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.after.package.statement"), "BLANK_LINES_AFTER_PACKAGE");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.before.imports"), "BLANK_LINES_BEFORE_IMPORTS");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.after.imports"), "BLANK_LINES_AFTER_IMPORTS");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.around.class"), "BLANK_LINES_AROUND_CLASS");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.after.class.header"), "BLANK_LINES_AFTER_CLASS_HEADER");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.after.anonymous.class.header"), "BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER");
    createOption(optionGroup, "Around field in interface:", "BLANK_LINES_AROUND_FIELD_IN_INTERFACE");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.around.field"), "BLANK_LINES_AROUND_FIELD");
    createOption(optionGroup, "Around method in interface:", "BLANK_LINES_AROUND_METHOD_IN_INTERFACE");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.around.method"), "BLANK_LINES_AROUND_METHOD");
    createOption(optionGroup, ApplicationBundle.message("editbox.blanklines.before.method.body"), "BLANK_LINES_BEFORE_METHOD_BODY");
    initCustomOptions(optionGroup, BLANK_LINES);

    return optionGroup.createPanel();
  }

  private JPanel createKeepBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup(BLANK_LINES_KEEP);

    createOption(optionGroup, ApplicationBundle.message("editbox.keep.blanklines.in.declarations"), "KEEP_BLANK_LINES_IN_DECLARATIONS");
    createOption(optionGroup, ApplicationBundle.message("editbox.keep.blanklines.in.code"), "KEEP_BLANK_LINES_IN_CODE");
    createOption(optionGroup, ApplicationBundle.message("editbox.keep.blanklines.before.rbrace"), "KEEP_BLANK_LINES_BEFORE_RBRACE");
    initCustomOptions(optionGroup, BLANK_LINES_KEEP);

    return optionGroup.createPanel();
  }

  private void initCustomOptions(OptionGroup optionGroup, String groupName) {
    for (Trinity<Class<? extends CustomCodeStyleSettings>, String, String> each : myCustomOptions.get(groupName)) {
      doCreateOption(optionGroup, each.third, new IntOption(each.first, each.second), each.second);
    }
  }

  private void createOption(OptionGroup optionGroup, String title, String fieldName) {
    if (myAllOptionsAllowed || myAllowedOptions.contains(fieldName)) {
      doCreateOption(optionGroup, title, new IntOption(fieldName), fieldName);
    }
  }

  private void doCreateOption(OptionGroup optionGroup, String title, IntOption option, String fieldName) {
    String renamed = myRenamedFields.get(fieldName);
    if (renamed != null) title = renamed;
    
    JLabel l = new JLabel(title);
    optionGroup.add(l, option.myTextField);
    myOptions.add(option);
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    for (IntOption option : myOptions) {
      option.setValue(option.getFieldValue(settings));
    }
  }

  public void apply(CodeStyleSettings settings) {
    for (IntOption option : myOptions) {
      option.setFieldValue(settings, option.getValue());
    }
  }

  public boolean isModified(CodeStyleSettings settings) {
    for (IntOption option : myOptions) {
      if (option.getFieldValue(settings) != option.getValue()) {
        return true;
      }
    }
    return false;

  }

  protected int getRightMargin() {
    return -1;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    //psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }

  public void showAllStandardOptions() {
    myAllOptionsAllowed = true;
    for (IntOption option : myOptions) {
      option.myTextField.setEnabled(true);
    }
  }

  public void showStandardOptions(String... optionNames) {
    if (myIsFirstUpdate) {
      Collections.addAll(myAllowedOptions, optionNames);
    }
    for (IntOption option : myOptions) {
      option.myTextField.setEnabled(false);
      for (String optionName : optionNames) {
        if (option.myTarget.getName().equals(optionName)) {
          option.myTextField.setEnabled(true);
          break;
        }
      }
    }
  }

  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass,
                               String fieldName,
                               String title,
                               String groupName) {
    if (myIsFirstUpdate) {
      myCustomOptions.putValue(groupName, (Trinity)Trinity.create(settingsClass, fieldName, title));
    }

    for (IntOption option : myOptions) {
      if (option.myTarget.getName().equals(fieldName)) {
        option.myTextField.setEnabled(true);
      }
    }
  }

  @Override
  public void renameStandardOption(String fieldName, String newTitle) {
    if (myIsFirstUpdate) {
      myRenamedFields.put(fieldName, newTitle);
    }
  }

  private static class IntOption {
    private final JTextField myTextField;
    private final Field myTarget;
    private Class<? extends CustomCodeStyleSettings> myTargetClass;

    private IntOption(String fieldName) {
      this(CodeStyleSettings.class, fieldName, false);
    }
    private IntOption(Class<? extends CustomCodeStyleSettings> targetClass, String fieldName) {
      this(targetClass, fieldName, false);
      myTargetClass = targetClass;
    }

    private IntOption(Class<?> fieldClass, String fieldName, boolean dummy) {
      try {
        myTarget = fieldClass.getField(fieldName);
      }
      catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
      myTextField = new JTextField(6);
      myTextField.setMinimumSize(new Dimension(30, myTextField.getMinimumSize().height));
    }

    private int getFieldValue(CodeStyleSettings settings) {
      try {
        return myTarget.getInt(myTargetClass == null ? settings : settings.getCustomSettings(myTargetClass));
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    public void setFieldValue(CodeStyleSettings settings, int value) {
      try {
        myTarget.setInt(myTargetClass == null ? settings : settings.getCustomSettings(myTargetClass), value);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    private int getValue() {
      int ret = 0;
      try {
        ret = Integer.parseInt(myTextField.getText());
        if (ret < 0) {
          ret = 0;
        }
        if (ret > 10) {
          ret = 10;
        }
      }
      catch (NumberFormatException e) {
        //bad number entered
      }
      return ret;
    }

    public void setValue(int fieldValue) {
      myTextField.setText(String.valueOf(fieldValue));
    }
  }
  
}
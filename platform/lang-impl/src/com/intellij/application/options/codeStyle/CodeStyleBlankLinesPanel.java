// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.presentation.CodeStyleSettingPresentation;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.*;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;

public class CodeStyleBlankLinesPanel extends CustomizableLanguageCodeStylePanel {

  private static final Logger LOG = Logger.getInstance(CodeStyleBlankLinesPanel.class);

  private final List<IntOption> myOptions = new ArrayList<>();
  private final Set<String> myAllowedOptions = new HashSet<>();
  private boolean myAllOptionsAllowed = false;
  private boolean myIsFirstUpdate = true;
  private final Map<String, @NlsContexts.Label String> myRenamedFields = new HashMap<>();

  private final MultiMap<String, IntOption> myCustomOptions = new MultiMap<>();

  private final JPanel myPanel = new JPanel(new GridBagLayout());

  public CodeStyleBlankLinesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  protected void init() {
    super.init();

    Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> settings = CodeStyleSettingPresentation
      .getStandardSettings(getSettingsType());

    List<IntOption> keepBlankLinesOptionsGroup =
      getOptions(getInstance().BLANK_LINES_KEEP, settings.get(new CodeStyleSettingPresentation.SettingsGroup(
        getInstance().BLANK_LINES_KEEP)));
    List<IntOption> blankLinesOptionsGroup =
      getOptions(getInstance().BLANK_LINES, settings.get(new CodeStyleSettingPresentation.SettingsGroup(
        getInstance().BLANK_LINES)));
    CodeStyleBlankLinesUI ui = new CodeStyleBlankLinesUI(getInstance(), myRenamedFields, keepBlankLinesOptionsGroup, blankLinesOptionsGroup);
    JPanel optionsPanel = ui.panel;
    optionsPanel.setBorder(JBUI.Borders.empty(0, 10));
    JScrollPane scroll = ScrollPaneFactory.createScrollPane(optionsPanel, true);
    scroll.setMinimumSize(new Dimension(optionsPanel.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width + 5, -1));
    scroll.setPreferredSize(scroll.getMinimumSize());

    myPanel
      .add(scroll,
           new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel
      .add(previewPanel,
           new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

    myIsFirstUpdate = false;
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.BLANK_LINES_SETTINGS;
  }

  private @NotNull List<IntOption> getOptions(@NotNull @NlsContexts.BorderTitle String groupName, @NotNull List<? extends CodeStyleSettingPresentation> settings) {
    final List<IntOption> groupOptions = new SmartList<>();
    for (CodeStyleSettingPresentation setting: settings) {
      if (myAllOptionsAllowed || myAllowedOptions.contains(setting.getFieldName())) {
        groupOptions.add(new IntOption(setting.getUiName(), setting.getFieldName()));
      }
    }
    groupOptions.addAll(myCustomOptions.get(groupName));
    myOptions.addAll(groupOptions);
    return sortOptions(groupOptions);
  }

  @Override
  protected void resetImpl(final @NotNull CodeStyleSettings settings) {
    for (IntOption option : myOptions) {
      option.setValue(option.getFieldValue(settings));
    }
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    for (IntOption option : myOptions) {
      option.myIntField.validateContent();
    }
    for (IntOption option : myOptions) {
      option.setFieldValue(settings, option.getValue());
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    for (IntOption option : myOptions) {
      if (option.getFieldValue(settings) != option.getValue()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  public void showAllStandardOptions() {
    myAllOptionsAllowed = true;
    for (IntOption option : myOptions) {
      option.myIntField.setEnabled(true);
    }
  }

  @Override
  public void showStandardOptions(String... optionNames) {
    if (myIsFirstUpdate) {
      Collections.addAll(myAllowedOptions, optionNames);
    }
    for (IntOption option : myOptions) {
      option.myIntField.setEnabled(false);
      for (String optionName : optionNames) {
        if (option.myTarget.getName().equals(optionName)) {
          option.myIntField.setEnabled(true);
          break;
        }
      }
    }
  }

  @Override
  public void showCustomOption(@NotNull Class<? extends CustomCodeStyleSettings> settingsClass,
                               @NonNls @NotNull String fieldName,
                               @NlsContexts.Label @NotNull String title,
                               @Nls @Nullable String groupName,
                               Object... options) {
    showCustomOption(settingsClass, fieldName, title, groupName, null, null, options);
  }

  @Override
  public void showCustomOption(@NotNull Class<? extends CustomCodeStyleSettings> settingsClass,
                               @NonNls @NotNull String fieldName,
                               @NlsContexts.Label @NotNull String title,
                               @Nls @Nullable String groupName,
                               @Nullable OptionAnchor anchor,
                               @NonNls @Nullable String anchorFieldName,
                               Object... options) {
    if (myIsFirstUpdate) {
      myCustomOptions.putValue(groupName, new IntOption(title, settingsClass, fieldName,anchor, anchorFieldName));
    }

    for (IntOption option : myOptions) {
      if (option.myTarget.getName().equals(fieldName)) {
        option.myIntField.setEnabled(true);
      }
    }
  }

  @Override
  public void renameStandardOption(@NonNls @NotNull String fieldName, @NlsContexts.Label @NotNull String newTitle) {
    if (myIsFirstUpdate) {
      myRenamedFields.put(fieldName, newTitle);
    }
    for (IntOption option : myOptions) {
      option.myIntField.invalidate();
    }
  }

  final class IntOption extends OrderedOption {
    final @NlsContexts.Label String myLabel;
    final IntegerField myIntField;
    private final Field myTarget;
    private Class<? extends CustomCodeStyleSettings> myTargetClass;
    private int myCurrValue = Integer.MAX_VALUE;

    private IntOption(@NlsContexts.Label @NotNull String label, String fieldName) {
      this(label, CommonCodeStyleSettings.class, fieldName, false);
    }

    private IntOption(@NlsContexts.Label @NotNull String label, Class<? extends CustomCodeStyleSettings> targetClass, String fieldName, @Nullable OptionAnchor anchor, @Nullable String anchorOptionName) {
      this(label, targetClass, fieldName, false, anchor, anchorOptionName);
      myTargetClass = targetClass;
    }

    // dummy is used to distinguish constructors
    private IntOption(@NlsContexts.Label @NotNull String label, Class<?> fieldClass, String fieldName, boolean dummy) {
      this(label, fieldClass, fieldName, dummy, null, null);
    }

    private IntOption(@NlsContexts.Label @NotNull String label, Class<?> fieldClass, String fieldName, boolean dummy, @Nullable OptionAnchor anchor, @Nullable String anchorOptionName) {
      super(fieldName, anchor, anchorOptionName);
      myLabel = label;
      try {
        myTarget = fieldClass.getField(fieldName);
      }
      catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
      myIntField = new IntegerField(null, 0, 10);
      myIntField.setColumns(6);
      myIntField.setMinimumSize(new Dimension(30, myIntField.getMinimumSize().height));
    }

    private int getFieldValue(CodeStyleSettings settings) {
      try {
        if (myTargetClass != null) {
          return myTarget.getInt(settings.getCustomSettings(myTargetClass));
        }
        CommonCodeStyleSettings commonSettings = settings.getCommonSettings(getDefaultLanguage());
        return myTarget.getInt(commonSettings);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    public void setFieldValue(CodeStyleSettings settings, int value) {
      try {
        if (myTargetClass != null) {
          myTarget.setInt(settings.getCustomSettings(myTargetClass), value);
        }
        else {
          CommonCodeStyleSettings commonSettings = settings.getCommonSettings(getDefaultLanguage());
          myTarget.setInt(commonSettings, value);
        }
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    private int getValue() {
      try {
        myCurrValue = Integer.parseInt(myIntField.getText());
        if (myCurrValue < 0) {
          myCurrValue = 0;
        }
        if (myCurrValue > 10) {
          myCurrValue = 10;
        }
      }
      catch (NumberFormatException e) {
        //bad number entered
        myCurrValue = 0;
      }
      return myCurrValue;
    }

    public void setValue(int fieldValue) {
      if (fieldValue != myCurrValue) {
        myCurrValue = fieldValue;
        myIntField.setText(String.valueOf(fieldValue));
      }
    }
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("title.blank.lines");
  }
}

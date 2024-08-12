// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.presentation.*;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.fields.CommaSeparatedIntegersField;
import com.intellij.ui.components.fields.valueEditors.CommaSeparatedIntegersValueEditor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;

public class WrappingAndBracesPanel extends OptionTableWithPreviewPanel {

  private final MultiMap<String, String> myGroupToFields = new MultiMap<>();
  private Map<String, SettingsGroup> myFieldNameToGroup;
  private final CommaSeparatedIntegersField mySoftMarginsEditor =
    new CommaSeparatedIntegersField(null, 0, CodeStyleConstraints.MAX_RIGHT_MARGIN,
                                    ApplicationBundle.message("settings.code.style.visual.guides.optional"));
  private final JComboBox<String> myWrapOnTypingCombo = new ComboBox<>(getInstance().WRAP_ON_TYPING_OPTIONS);
  private final CommaSeparatedIdentifiersField myCommaSeparatedIdentifiersField = new CommaSeparatedIdentifiersField();

  public WrappingAndBracesPanel(CodeStyleSettings settings) {
    super(settings);
    MarginOptionsUtil.customizeWrapOnTypingCombo(myWrapOnTypingCombo, settings);
    init();
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, mySoftMarginsEditor);
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myWrapOnTypingCombo);
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_AND_BRACES_SETTINGS;
  }

  @Override
  protected void addOption(@NotNull String fieldName, @NotNull String title, @Nullable String groupName) {
    super.addOption(fieldName, title, groupName);
    if (groupName != null) {
      myGroupToFields.putValue(groupName, fieldName);
    }
  }

  @Override
  protected void addOption(@NotNull String fieldName, @NotNull String title, @Nullable String groupName,
                           String @NotNull [] options, int @NotNull [] values) {
    super.addOption(fieldName, title, groupName, options, values);
    if (groupName == null) {
      myGroupToFields.putValue(title, fieldName);
    }
  }

  @Override
  protected void initTables() {
    for (Map.Entry<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> entry :
      CodeStyleSettingPresentation.getStandardSettings(getSettingsType()).entrySet()) {
      CodeStyleSettingPresentation.SettingsGroup group = entry.getKey();
      for (CodeStyleSettingPresentation setting : entry.getValue()) {
        String fieldName = setting.getFieldName();
        String uiName = setting.getUiName();
        if (setting instanceof CodeStyleBoundedIntegerSettingPresentation intSetting) {
          int defaultValue = intSetting.getDefaultValue();
          addOption(fieldName, uiName, group.name, intSetting.getLowerBound(), intSetting.getUpperBound(), defaultValue,
                    getDefaultIntValueRenderer(fieldName));
        }
        else if (setting instanceof CodeStyleSelectSettingPresentation selectSetting) {
          addOption(fieldName, uiName, group.name, selectSetting.getOptions(), selectSetting.getValues());
        }
        else if (setting instanceof CodeStyleSoftMarginsPresentation) {
          addSoftMarginsOption(fieldName, uiName, group.name);
          showOption(fieldName);
        }
        else if (setting instanceof CodeStyleCommaSeparatedIdentifiersPresentation) {
          addCustomOption(new CommaSeparatedIdentifiersOption(fieldName, uiName, group.name));
        }
        else {
          addOption(fieldName, uiName, group.name);
        }
      }
    }
  }

  private Function<Integer,String> getDefaultIntValueRenderer(@NotNull String fieldName) {
    if ("RIGHT_MARGIN".equals(fieldName)) {
      return integer -> MarginOptionsUtil.getDefaultRightMarginText(getSettings());
    }
    else {
      return integer -> ApplicationBundle.message("integer.field.value.default");
    }
  }

  protected SettingsGroup getAssociatedSettingsGroup(String fieldName) {
    if (myFieldNameToGroup == null) {
      myFieldNameToGroup = new HashMap<>();
      Set<String> groups = myGroupToFields.keySet();
      for (String group : groups) {
        Collection<String> fields = myGroupToFields.get(group);
        SettingsGroup settingsGroup = new SettingsGroup(group, fields);
        for (String field : fields) {
          myFieldNameToGroup.put(field, settingsGroup);
        }
      }
    }
    return myFieldNameToGroup.get(fieldName);
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("settings.code.style.tab.title.wrapping.and.braces");
  }

  protected record SettingsGroup(@NotNull String title,
                         @NotNull Collection<String> commonCodeStyleSettingFieldNames) {
  }


  private void addSoftMarginsOption(@NotNull String optionName, @NotNull String title, @Nullable String groupName) {
    Language language = getDefaultLanguage();
    if (language != null) {
      addCustomOption(new SoftMarginsOption(language, optionName, title, groupName));
    }
  }

  private static final class SoftMarginsOption extends Option {

    private final Language myLanguage;

    private SoftMarginsOption(@NotNull Language language,
                              @NotNull String optionName,
                              @NotNull String title,
                              @Nullable String groupName) {
      super(optionName, title, groupName, null, null);
      myLanguage = language;
    }

    @Override
    public Object getValue(CodeStyleSettings settings) {
      CommonCodeStyleSettings langSettings = settings.getCommonSettings(myLanguage);
      return langSettings.getSoftMargins();
    }

    @Override
    public void setValue(Object value, CodeStyleSettings settings) {
      settings.setSoftMargins(myLanguage, castToIntList(value));
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  private final class CommaSeparatedIdentifiersOption extends FieldOption {

    private CommaSeparatedIdentifiersOption(@NotNull String optionName,
                                            @NotNull String title,
                                            @Nullable String groupName) {
      super(null, optionName, title, groupName, null, null);
    }

    @Override
    public Object getValue(CodeStyleSettings settings) {
      try {
        return field.get(getSettings(settings));
      }
      catch (IllegalAccessException e) {
        return null;
      }
    }

    @Override
    public void setValue(Object value, CodeStyleSettings settings) {
      try {
        field.set(getSettings(settings), value);
      }
      catch (IllegalAccessException ignored) {
      }
    }
  }

  private static List<Integer> castToIntList(@Nullable Object value) {
    if (value instanceof List && ((List<?>)value).size() > 0 && ((List<?>)value).get(0) instanceof Integer) {
      //noinspection unchecked
      return (List<Integer>)value;
    }
    return Collections.emptyList();
  }

  @Override
  protected @Nullable JComponent getCustomValueRenderer(@NotNull String optionName, @NotNull Object value) {
    if (CodeStyleSoftMarginsPresentation.OPTION_NAME.equals(optionName)) {
      JLabel softMarginsLabel = new JLabel(getSoftMarginsString(castToIntList(value)));
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, softMarginsLabel);
      return softMarginsLabel;
    }
    else if ("WRAP_ON_TYPING".equals(optionName)) {
      if (value.equals(ApplicationBundle.message("wrapping.wrap.on.typing.default"))) {
        JLabel wrapLabel = new JLabel(MarginOptionsUtil.getDefaultWrapOnTypingText(getSettings()));
        UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, wrapLabel);
        return wrapLabel;
      }
    }
    else if ("BUILDER_METHODS".equals(optionName)) {
      if (value instanceof @Nls String strValue) {
        String tooltipText = ApplicationBundle.message("settings.code.style.builder.methods.tooltip");
        if (StringUtil.isEmptyOrSpaces(strValue)) {
          ColoredLabel hintLabel = new ColoredLabel(ApplicationBundle.message("settings.code.style.builder.method.names"), JBColor.gray);
          UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, hintLabel);
          hintLabel.setToolTipText(tooltipText);
          return hintLabel;
        }
        else if (!strValue.contains(",")){
          JLabel valueLabel = new JLabel(strValue);
          valueLabel.setToolTipText(tooltipText);
          return valueLabel;
        }
      }
    }
    return super.getCustomValueRenderer(optionName, value);
  }

  private @NotNull @NlsContexts.Label String getSoftMarginsString(@NotNull List<Integer> intList) {
    if (intList.size() > 0) {
      return CommaSeparatedIntegersValueEditor.intListToString(intList);
    }
    return MarginOptionsUtil.getDefaultVisualGuidesText(getSettings());
  }

  @Override
  protected @Nullable JComponent getCustomNodeEditor(@NotNull MyTreeNode node) {
    String optionName = node.getKey().getOptionName();
    if (CodeStyleSoftMarginsPresentation.OPTION_NAME.equals(optionName)) {
      mySoftMarginsEditor.setValue(castToIntList(node.getValue()));
      return mySoftMarginsEditor;
    }
    else if ("WRAP_ON_TYPING".equals(optionName)) {
      Object value = node.getValue();
      if (value instanceof String) {
        for (int i = 0; i < getInstance().WRAP_ON_TYPING_OPTIONS.length; i++) {
          if (getInstance().WRAP_ON_TYPING_OPTIONS[i].equals(value)) {
            myWrapOnTypingCombo.setSelectedIndex(i);
            break;
          }
        }
      }
      return myWrapOnTypingCombo;
    }
    else if (node.getKey() instanceof CommaSeparatedIdentifiersOption) {
      myCommaSeparatedIdentifiersField.setValueName(node.getText());
      String currValue = (String)node.getValue();
      myCommaSeparatedIdentifiersField.setText(currValue);
      myCommaSeparatedIdentifiersField.getEditor().setDefaultValue(currValue);
      return myCommaSeparatedIdentifiersField;
    }
    return super.getCustomNodeEditor(node);
  }

  @Override
  protected @Nullable Object getCustomNodeEditorValue(@NotNull JComponent customEditor) {
    if (customEditor instanceof CommaSeparatedIntegersField) {
      return ((CommaSeparatedIntegersField)customEditor).getValue();
    }
    else if (customEditor == myWrapOnTypingCombo) {
      int i = myWrapOnTypingCombo.getSelectedIndex();
      return i >= 0 ? getInstance().WRAP_ON_TYPING_OPTIONS[i] : null;
    }
    else if (customEditor == myCommaSeparatedIdentifiersField) {
      return myCommaSeparatedIdentifiersField.getEditor().getValue();
    }
    return super.getCustomNodeEditorValue(customEditor);
  }
}
/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.ui;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.presentation.CodeStyleSelectSettingPresentation;
import com.intellij.psi.codeStyle.presentation.CodeStyleSettingPresentation;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.psi.codeStyle.presentation.CodeStyleSettingPresentation.SettingsGroup;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Roman.Shein
 * @since 03.08.2015.
 */
public class CodeStyleSettingsNameProvider implements CodeStyleSettingsCustomizable {

  protected Map<SettingsType, Map<SettingsGroup, List<CodeStyleSettingPresentation>>> mySettings =
    ContainerUtil.newHashMap();
  private Map<SettingsType, Map<SettingsGroup, List<CodeStyleSettingPresentation>>> standardSettings =
    ContainerUtil.newHashMap();

  public CodeStyleSettingsNameProvider() {
    for (SettingsType settingsType : SettingsType.values()) {
      standardSettings.put(settingsType, CodeStyleSettingPresentation.getStandardSettings(settingsType));
    }
  }

  protected void addSetting(@NotNull SettingsGroup group, @NotNull CodeStyleSettingPresentation setting, @Nullable OptionAnchor anchor,
                            @Nullable String anchorFieldName) {
    for (Map.Entry<SettingsType, Map<SettingsGroup, List<CodeStyleSettingPresentation>>> entry: mySettings.entrySet()) {
      if (entry.getValue().containsKey(group)) {
        addSetting(entry.getKey(), group, setting, anchor, anchorFieldName);
        return;
      }
    }
    addSetting(SettingsType.LANGUAGE_SPECIFIC, group, setting, anchor, anchorFieldName);
  }

  protected void addSetting(@NotNull SettingsType settingsType, @NotNull SettingsGroup group, @NotNull CodeStyleSettingPresentation setting,
                            @Nullable OptionAnchor anchor, @Nullable String anchorFieldName) {
    Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> groups = mySettings.get(settingsType);
    if (groups == null) {
      groups = ContainerUtil.newLinkedHashMap();
    }
    List<CodeStyleSettingPresentation> settingsList = groups.get(group);
    if (settingsList == null) {
      settingsList = ContainerUtil.newLinkedList();
    }
    if (settingsList.contains(setting)) return;
    if (anchor != null && anchorFieldName != null) {
      CodeStyleSettingPresentation anchorSettingRepresentation = new CodeStyleSettingPresentation(anchorFieldName, anchorFieldName);
      int insertIndex = settingsList.indexOf(anchorSettingRepresentation);
      if (insertIndex < 0) {
        insertIndex = settingsList.size();
      } else {
        switch (anchor) {
          case BEFORE:
            break;
          case AFTER:
            insertIndex++;
            break;
          case NONE:
            insertIndex = settingsList.size();
        }
      }
      settingsList.add(insertIndex, setting);
    } else {
      settingsList.add(setting);
    }
    groups.put(group, settingsList);
  }

  @Override
  public void showAllStandardOptions() {
    for (SettingsType settingsType : SettingsType.values()) {
      Map<SettingsGroup, List<CodeStyleSettingPresentation>> standardGroups = standardSettings.get(settingsType);
      for (Map.Entry<SettingsGroup, List<CodeStyleSettingPresentation>> entry : standardGroups.entrySet()) {
        for (CodeStyleSettingPresentation setting: entry.getValue()) {
          addSetting(settingsType, entry.getKey(), setting, null, null);
        }
      }
    }
  }

  @Override
  public void showStandardOptions(String... optionNames) {
    List<String> options = Arrays.asList(optionNames);
    for (SettingsType settingsType : SettingsType.values()) {
      Map<SettingsGroup, List<CodeStyleSettingPresentation>> standardGroups = standardSettings.get(settingsType);
      for (Map.Entry<SettingsGroup, List<CodeStyleSettingPresentation>> entry : standardGroups.entrySet()) {
        for (CodeStyleSettingPresentation setting: entry.getValue()) {
          if (options.contains(setting.getFieldName())) {
            addSetting(settingsType, entry.getKey(), setting, null, null);
          }
        }
      }
    }
  }

  @Override
  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, @NotNull String fieldName, @NotNull String title, @Nullable String groupName, Object... options) {
    showCustomOption(settingsClass, fieldName, title, groupName, null, null, options);
  }

  @Override
  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, @NotNull String fieldName, @NotNull String title,
                               @Nullable String groupName, @Nullable OptionAnchor anchor, @Nullable String anchorFieldName, Object... options) {
    if (options.length == 2) {
      addSetting(new SettingsGroup(groupName), new CodeStyleSelectSettingPresentation(fieldName, title, (int[])options[1],
                                                                                        (String[])options[0]), anchor, anchorFieldName);
    } else {
      addSetting(new SettingsGroup(groupName), new CodeStyleSettingPresentation(fieldName, title), anchor, anchorFieldName);
    }
  }

  @Override
  public void renameStandardOption(String fieldName, String newTitle) {
    for (SettingsType settingsType : SettingsType.values()) {
      Map<SettingsGroup, List<CodeStyleSettingPresentation>> standardGroups = mySettings.get(settingsType);
      if (standardGroups == null) {
        continue;
      }
      for (Map.Entry<SettingsGroup, List<CodeStyleSettingPresentation>> entry : standardGroups.entrySet()) {
        for (CodeStyleSettingPresentation setting: entry.getValue()) {
          if (setting.getFieldName().equals(fieldName)) {
            setting.setUiName(newTitle);
            return;
          }
        }
      }
    }
  }

  @Override
  public void moveStandardOption(String fieldName, String newGroup) {
    for (SettingsType settingsType : SettingsType.values()) {
      Map<SettingsGroup, List<CodeStyleSettingPresentation>> standardGroups = mySettings.get(settingsType);
      if (standardGroups == null) {
        standardGroups = ContainerUtil.newLinkedHashMap();
        mySettings.put(settingsType, standardGroups);
      }
      for (Map.Entry<SettingsGroup, List<CodeStyleSettingPresentation>> entry : standardGroups.entrySet()) {
        CodeStyleSettingPresentation moveSetting = null;
        for (CodeStyleSettingPresentation setting: entry.getValue()) {
          if (setting.getFieldName().equals(fieldName)) {
            moveSetting = setting;
            break;
          }
        }
        if (moveSetting != null) {
          entry.getValue().remove(moveSetting);
          addSetting(new SettingsGroup(newGroup), moveSetting, null, null);
        }
      }
    }
  }

  public static String getSettingsTypeName(LanguageCodeStyleSettingsProvider.SettingsType settingsType) {
    switch (settingsType) {
      case BLANK_LINES_SETTINGS: return ApplicationBundle.message("title.blank.lines");
      case SPACING_SETTINGS: return ApplicationBundle.message("title.spaces");
      case WRAPPING_AND_BRACES_SETTINGS: return ApplicationBundle.message("wrapping.and.braces");
      case INDENT_SETTINGS: return ApplicationBundle.message("title.tabs.and.indents");
      case LANGUAGE_SPECIFIC: return "Language-specific"; //TODO should load from ApplciationBundle here
      default: throw new IllegalArgumentException("Unknown settings type: " + settingsType);
    }
  }

  public void addSettings(LanguageCodeStyleSettingsProvider provider) {
    for (SettingsType settingsType : LanguageCodeStyleSettingsProvider.SettingsType.values()) {
      provider.customizeSettings(this, settingsType);
    }
  }

  public static Value getValue(final CodeStyleSettingPresentation representation, List<Value> values) {
    Value myValue = ContainerUtil.find(values, value -> {
      return value.state == Value.STATE.SELECTED && value.name.equals(representation.getFieldName());
      //return value.name.equals(representation.getFieldName()); //TODO this is here only to test the UI!!
    });
    return myValue;
  }

  public String getSettings(List<Value> values) {
    StringBuilder builder = new StringBuilder();
    for (SettingsType settingsType : LanguageCodeStyleSettingsProvider.SettingsType.values()) {
      builder.append("<br><b><u>").append(getSettingsTypeName(settingsType)).append("</u></b>");
      Map<SettingsGroup, List<CodeStyleSettingPresentation>> groups = mySettings.get(settingsType);
      if (groups != null) {
        for (Map.Entry<SettingsGroup, List<CodeStyleSettingPresentation>> entry : groups.entrySet()) {
          boolean firstSettingGroupTop = entry.getKey().isNull();
          boolean groupReported = false;
          for (final CodeStyleSettingPresentation setting : entry.getValue()) {
            Value myValue = ContainerUtil.find(values,
                                               value -> value.state == Value.STATE.SELECTED && value.name.equals(setting.getFieldName()));
            if (myValue == null) {
              continue;
            }
            if (!groupReported) {
              if (firstSettingGroupTop) {
                builder.append("<b>");
              } else {
                builder.append("<br><b>").append(entry.getKey().name).append("</b>");
              }
            }
            builder.append("<br>");
            String postNameSign = setting.getUiName().endsWith(":") ?  " " : ": ";
            builder.append(setting.getUiName()).append(postNameSign).append(setting.getValueUiName(myValue.value));
            if (!groupReported) {
              if (firstSettingGroupTop) {
                builder.append("</b>");
              }
            }
            groupReported = true;
          }
        }
      }
    }
    return builder.toString();
  }
}

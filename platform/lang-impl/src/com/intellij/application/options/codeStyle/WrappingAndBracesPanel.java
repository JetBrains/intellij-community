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
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.presentation.CodeStyleBoundedIntegerSettingPresentation;
import com.intellij.psi.codeStyle.presentation.CodeStyleSelectSettingPresentation;
import com.intellij.psi.codeStyle.presentation.CodeStyleSettingPresentation;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WrappingAndBracesPanel extends OptionTableWithPreviewPanel {
  private MultiMap<String, String> myGroupToFields = new MultiMap<>();
  private Map<String, SettingsGroup> myFieldNameToGroup;

  public WrappingAndBracesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
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
                           @NotNull String[] options, @NotNull int[] values) {
    super.addOption(fieldName, title, groupName, options, values);
    if (groupName == null) {
      myGroupToFields.putValue(title, fieldName);
    }
  }

  @Override
  protected void initTables() {
    for (Map.Entry<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> entry:
      CodeStyleSettingPresentation.getStandardSettings(getSettingsType()).entrySet()) {
      CodeStyleSettingPresentation.SettingsGroup group = entry.getKey();
      for (CodeStyleSettingPresentation setting: entry.getValue()) {
        //TODO this is ugly, but is the fastest way to make Options UI API and settings representation API connect
        String fieldName = setting.getFieldName();
        String uiName = setting.getUiName();
        if (setting instanceof CodeStyleBoundedIntegerSettingPresentation) {
          CodeStyleBoundedIntegerSettingPresentation intSetting = (CodeStyleBoundedIntegerSettingPresentation) setting;
          int defaultValue = intSetting.getDefaultValue();
          addOption(fieldName, uiName, group.name, intSetting.getLowerBound(), intSetting.getUpperBound(), defaultValue, intSetting.getValueUiName(
            defaultValue));
        } else if (setting instanceof CodeStyleSelectSettingPresentation) {
          CodeStyleSelectSettingPresentation selectSetting = (CodeStyleSelectSettingPresentation) setting;
          addOption(fieldName, uiName, group.name, selectSetting.getOptions(), selectSetting.getValues());
        } else {
          addOption(fieldName, uiName, group.name);
        }
      }
    }
  }

  protected SettingsGroup getAssociatedSettingsGroup(String fieldName) {
    if (myFieldNameToGroup == null) {
      myFieldNameToGroup = ContainerUtil.newHashMap();
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
  protected String getTabTitle() {
    return ApplicationBundle.message("wrapping.and.braces");
  }

  protected static class SettingsGroup {
    public final String title;
    public final Collection<String> commonCodeStyleSettingFieldNames;

    public SettingsGroup(@NotNull String title,
                         @NotNull Collection<String> commonCodeStyleSettingFieldNames) {
      this.title = title;
      this.commonCodeStyleSettingFieldNames = commonCodeStyleSettingFieldNames;
    }
  }
}
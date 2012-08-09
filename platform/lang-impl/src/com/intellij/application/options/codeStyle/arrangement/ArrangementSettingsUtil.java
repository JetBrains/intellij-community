/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.HierarchicalArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 9:14 AM
 */
public class ArrangementSettingsUtil {

  private static final EntryTypeHelper ENTRY_TYPE_HELPER = new EntryTypeHelper();
  private static final ModifierHelper  MODIFIER_HELPER   = new ModifierHelper();

  private ArrangementSettingsUtil() {
  }

  @Nullable
  public static HierarchicalArrangementSettingsNode buildTreeStructure(@NotNull ArrangementSettingsNode modelNode) {
    // TODO den implement
    return new HierarchicalArrangementSettingsNode(modelNode);
  }
  
  @NotNull
  public static Map<ArrangementSettingType, List<?>> buildAvailableOptions(@NotNull ArrangementStandardSettingsAware filter) {
    Map<ArrangementSettingType, List<?>> result = new EnumMap<ArrangementSettingType, List<?>>(ArrangementSettingType.class);
    processData(filter, result, ArrangementEntryType.values(), ENTRY_TYPE_HELPER);
    processData(filter, result, ArrangementModifier.values(), MODIFIER_HELPER);
    return result;
  }

  private static <T> void processData(@NotNull ArrangementStandardSettingsAware filter,
                                      Map<ArrangementSettingType, List<?>> result,
                                      @NotNull T[] values,
                                      @NotNull Helper<T> helper)
  {
    List<T> data = null;
    for (T v : values) {
      if (!helper.isSupported(v, filter)) {
        continue;
      }
      if (data == null) {
        data = new ArrayList<T>();
      }
      data.add(v);
    }
    if (data != null) {
      result.put(helper.getType(), data);
    }
  }

  private interface Helper<T> {
    @NotNull ArrangementSettingType getType();
    boolean isSupported(@NotNull T data, @NotNull ArrangementStandardSettingsAware filter);
  }

  private static class EntryTypeHelper implements Helper<ArrangementEntryType> {
    @NotNull
    @Override
    public ArrangementSettingType getType() {
      return ArrangementSettingType.TYPE;
    }

    @Override
    public boolean isSupported(@NotNull ArrangementEntryType data, @NotNull ArrangementStandardSettingsAware filter) {
      return filter.isSupported(data);
    }
  }
  
  private static class ModifierHelper implements Helper<ArrangementModifier> {
    @NotNull
    @Override
    public ArrangementSettingType getType() {
      return ArrangementSettingType.MODIFIER;
    }

    @Override
    public boolean isSupported(@NotNull ArrangementModifier data, @NotNull ArrangementStandardSettingsAware filter) {
      return filter.isSupported(data);
    }
  }
}

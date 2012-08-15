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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.HierarchicalArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 9:14 AM
 */
public class ArrangementSettingsUtil {

  public static final DataKey<ArrangementNodeComponent>         NODE_COMPONENT  = DataKey.create("Arrangement.Rule.Editor.Node.Component");
  public static final DataKey<ArrangementStandardSettingsAware> FILTER          = DataKey.create("Arrangement.Rule.Editor.Settings.Filter");
  public static final DataKey<ArrangementNodeDisplayManager>    DISPLAY_MANAGER = DataKey.create("Arrangement.Rule.Editor.Display.Manager");
  public static final DataKey<ArrangementMatcherSettings>       SETTINGS        = DataKey.create("Arrangement.Rule.Editor.Matcher.Settings");
  public static final DataKey<JComponent>                       TREE            = DataKey.create("Arrangement.Rule.Editor.Tree");

  private static final EntryTypeHelper ENTRY_TYPE_HELPER = new EntryTypeHelper();
  private static final ModifierHelper  MODIFIER_HELPER   = new ModifierHelper();

  private ArrangementSettingsUtil() {
  }

  @Nullable
  public static ArrangementSettingsNode getSettingsNode(@NotNull DataContext context) {
    ArrangementNodeComponent nodeComponent = NODE_COMPONENT.getData(context);
    return nodeComponent == null ? null : nodeComponent.getSettingsNode();
  }

  @Nullable
  public static HierarchicalArrangementSettingsNode buildTreeStructure(@NotNull ArrangementSettingsNode modelNode) {
    // TODO den implement
    return new HierarchicalArrangementSettingsNode(modelNode);
  }

  /**
   * Serves for the same purposes as {@link #buildAvailableOptions(ArrangementStandardSettingsAware, ArrangementMatcherSettings)} but
   * retrieves necessary information from the given context.
   *
   * @param context  target information holder
   * @return map which contains information on what new new settings are available at the current situation
   */
  @NotNull
  public static Map<ArrangementSettingType, Collection<?>> buildAvailableOptions(@NotNull DataContext context) {
    ArrangementStandardSettingsAware filter = FILTER.getData(context);
    if (filter == null) {
      return Collections.emptyMap();
    }
    
    return buildAvailableOptions(filter, SETTINGS.getData(context));
  }

  /**
   * Allows to answer what new settings are available for a particular {@link ArrangementMatcherSettings arrangement matcher rules}.
   *
   * @param filter    filter to use
   * @param settings  object that encapsulates information about current arrangement matcher settings
   * @return          map which contains information on what new new settings are available at the current situation
   */
  @NotNull
  public static Map<ArrangementSettingType, Collection<?>> buildAvailableOptions(@NotNull ArrangementStandardSettingsAware filter,
                                                                                 @Nullable ArrangementMatcherSettings settings)
  {
    Map<ArrangementSettingType, Collection<?>> result = new EnumMap<ArrangementSettingType, Collection<?>>(ArrangementSettingType.class);
    processData(filter, settings, result, ArrangementEntryType.values(), ENTRY_TYPE_HELPER);
    processData(filter, settings, result, ArrangementModifier.values(), MODIFIER_HELPER);
    return result;
  }

  private static <T> void processData(@NotNull ArrangementStandardSettingsAware filter,
                                      @Nullable ArrangementMatcherSettings settings,
                                      Map<ArrangementSettingType, Collection<?>> result,
                                      @NotNull T[] values,
                                      @NotNull Helper<T> helper)
  {
    List<T> data = null;
    for (T v : values) {
      if (!helper.isEnabled(v, filter, settings)) {
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

  @Nullable
  public static Point getLocationOnScreen(@NotNull JComponent component) {
    int dx = 0;
    int dy = 0;
    for (Container c = component; c != null; c = c.getParent()) {
      if (c.isShowing()) {
        Point locationOnScreen = c.getLocationOnScreen();
        locationOnScreen.translate(dx, dy);
        return locationOnScreen;
      }
      else {
        Point location = c.getLocation();
        dx += location.x;
        dy += location.y;
      }
    }
    return null;
  }
  
  private interface Helper<T> {
    @NotNull ArrangementSettingType getType();
    boolean isEnabled(@NotNull T data, @NotNull ArrangementStandardSettingsAware filter, @Nullable ArrangementMatcherSettings settings);
  }

  private static class EntryTypeHelper implements Helper<ArrangementEntryType> {
    @NotNull
    @Override
    public ArrangementSettingType getType() {
      return ArrangementSettingType.TYPE;
    }

    @Override
    public boolean isEnabled(@NotNull ArrangementEntryType data,
                             @NotNull ArrangementStandardSettingsAware filter,
                             @Nullable ArrangementMatcherSettings settings)
    {
      return filter.isEnabled(data, settings);
    }
  }
  
  private static class ModifierHelper implements Helper<ArrangementModifier> {
    @NotNull
    @Override
    public ArrangementSettingType getType() {
      return ArrangementSettingType.MODIFIER;
    }

    @Override
    public boolean isEnabled(@NotNull ArrangementModifier data,
                             @NotNull ArrangementStandardSettingsAware filter,
                             @Nullable ArrangementMatcherSettings settings)
    {
      return filter.isEnabled(data, settings);
    }
  }
}

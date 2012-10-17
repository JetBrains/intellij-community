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
package com.intellij.psi.codeStyle.arrangement.settings;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/15/12 10:24 AM
 */
public class DefaultArrangementSettingsRepresentationManager implements ArrangementStandardSettingsRepresentationAware {

  @NotNull public static final DefaultArrangementSettingsRepresentationManager INSTANCE =
    new DefaultArrangementSettingsRepresentationManager();

  @NotNull private static final TObjectIntHashMap<Object> WEIGHTS = new TObjectIntHashMap<Object>();

  static {
    Object[] ids = {
      
      // Types.
      ArrangementEntryType.FIELD, ArrangementEntryType.CONSTRUCTOR, ArrangementEntryType.METHOD, ArrangementEntryType.CLASS,
      ArrangementEntryType.INTERFACE, ArrangementEntryType.ENUM,

      ArrangementEntryType.STATIC_INIT, ArrangementEntryType.CONST, ArrangementEntryType.VAR,
      ArrangementEntryType.PROPERTY, ArrangementEntryType.EVENT_HANDLER,
      
      // Visibility.
      ArrangementModifier.PUBLIC, ArrangementModifier.PROTECTED, ArrangementModifier.PACKAGE_PRIVATE, ArrangementModifier.PRIVATE,
      
      // Other common modifiers.
      ArrangementModifier.STATIC, ArrangementModifier.FINAL,

      ArrangementModifier.OVERRIDE,
      
      // Semi-common modifiers.
      ArrangementModifier.ABSTRACT,
      
      // Method-specific modifiers.
      ArrangementModifier.SYNCHRONIZED,
      
      // Field-specific modifiers.
      ArrangementModifier.TRANSIENT, ArrangementModifier.VOLATILE
    };
    for (int i = 0; i < ids.length; i++) {
      WEIGHTS.put(ids[i], i);
    }
  }

  @NotNull
  private static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      if (WEIGHTS.containsKey(o1) && WEIGHTS.containsKey(o2)) {
        return WEIGHTS.get(o1) - WEIGHTS.get(o2);
      }
      else if (WEIGHTS.containsKey(o1) && !WEIGHTS.containsKey(o2)) {
        return -1;
      }
      else if (!WEIGHTS.containsKey(o1) && WEIGHTS.containsKey(o2)) {
        return 1;
      }
      else {
        return o1.hashCode() - o2.hashCode();
      }
    }
  };

  private static final Map<ArrangementGroupingType, String> GROUPING_TYPES = ContainerUtil.newHashMap();
  static {
    GROUPING_TYPES.put(ArrangementGroupingType.GETTERS_AND_SETTERS,
                       ApplicationBundle.message("arrangement.settings.groups.getters.and.setters.together"));
    GROUPING_TYPES.put(ArrangementGroupingType.OVERRIDDEN_METHODS,
                       ApplicationBundle.message("arrangement.settings.groups.overridden.methods"));
    GROUPING_TYPES.put(ArrangementGroupingType.DEPENDENT_METHODS,
                       ApplicationBundle.message("arrangement.settings.groups.dependent.methods"));
    GROUPING_TYPES.put(ArrangementGroupingType.GROUP_PROPERTY_FIELD_WITH_GETTER_SETTER,
                       ApplicationBundle.message("arrangement.settings.groups.property.field"));
    assert GROUPING_TYPES.size() == ArrangementGroupingType.values().length;
  }

  private static final Map<ArrangementEntryOrderType, String> ORDER_TYPES = ContainerUtil.newHashMap();
  static {
    ORDER_TYPES.put(ArrangementEntryOrderType.KEEP, ApplicationBundle.message("arrangement.settings.order.type.keep"));
    ORDER_TYPES.put(ArrangementEntryOrderType.BY_NAME, ApplicationBundle.message("arrangement.settings.order.type.by.name"));
    ORDER_TYPES.put(ArrangementEntryOrderType.DEPTH_FIRST, ApplicationBundle.message("arrangement.settings.order.type.depth.first"));
    ORDER_TYPES.put(ArrangementEntryOrderType.BREADTH_FIRST, ApplicationBundle.message("arrangement.settings.order.type.breadth.first"));
    assert ORDER_TYPES.size() == ArrangementEntryOrderType.values().length;
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementEntryType type) {
    return getDisplayValue(type.toString());
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementModifier modifier) {
    return getDisplayValue(modifier.toString());
  }

  @NotNull
  private static String getDisplayValue(@NotNull String s) {
    return s.toLowerCase().replace("_", " ");
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementGroupingType groupingType) {
    return GROUPING_TYPES.get(groupingType);
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementEntryOrderType orderType) {
    return ORDER_TYPES.get(orderType);
  }

  @NotNull
  @Override
  public <T> List<T> sort(@NotNull Collection<T> ids) {
    List<T> result = new ArrayList<T>(ids);
    Collections.sort(result, COMPARATOR);
    return result;
  }
}

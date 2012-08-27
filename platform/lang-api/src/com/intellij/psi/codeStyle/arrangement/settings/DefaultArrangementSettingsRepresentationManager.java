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

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
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
      ArrangementEntryType.FIELD, ArrangementEntryType.METHOD, ArrangementEntryType.CLASS, ArrangementEntryType.INTERFACE,
      ArrangementEntryType.ENUM,
      
      // Visibility.
      ArrangementModifier.PUBLIC, ArrangementModifier.PROTECTED, ArrangementModifier.PACKAGE_PRIVATE, ArrangementModifier.PRIVATE,
      
      // Other common modifiers.
      ArrangementModifier.STATIC, ArrangementModifier.FINAL,
      
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

  @NotNull private static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
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
  public <T> List<T> sort(@NotNull Collection<T> ids) {
    List<T> result = new ArrayList<T>(ids);
    Collections.sort(result, COMPARATOR);
    return result;
  }
}

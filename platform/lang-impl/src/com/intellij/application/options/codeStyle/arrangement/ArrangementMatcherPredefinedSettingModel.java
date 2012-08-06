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
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementEntryNodeSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsFilter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 4:51 PM
 */
public class ArrangementMatcherPredefinedSettingModel {

  @NotNull private final List<ArrangementEntryType> mySupportedTypes     = new ArrayList<ArrangementEntryType>();
  @NotNull private final List<ArrangementModifier>  mySupportedModifiers = new ArrayList<ArrangementModifier>();

  @NotNull private final ArrangementEntryNodeSettings mySettings;
  @NotNull private final ArrangementSettingsFilter    myFilter;

  public ArrangementMatcherPredefinedSettingModel(@NotNull ArrangementEntryNodeSettings settings,
                                                  @NotNull ArrangementSettingsFilter filter)
  {
    mySettings = settings;
    myFilter = filter;
    for (ArrangementEntryType type : ArrangementEntryType.values()) {
      if (myFilter.isSupported(type)) {
        mySupportedTypes.add(type);
      }
    }
    for (ArrangementModifier modifier : ArrangementModifier.values()) {
      if (myFilter.isSupported(modifier)) {
        mySupportedModifiers.add(modifier);
      }
    }
  }

  @NotNull
  public List<ArrangementMatcherKey> getAvailableKeys() {
    List<ArrangementMatcherKey> result = new ArrayList<ArrangementMatcherKey>();
    ArrangementEntryType type = mySettings.getType();
    if (type == null && !mySupportedTypes.isEmpty()) {
      // Start with the type if it's not defined yet
      result.add(ArrangementMatcherKey.TYPE);
      return result;
    }
    // TODO den implement
    result.add(ArrangementMatcherKey.MODIFIER);
    return result;
  }

  @NotNull
  public List<?> getAvailableValues(@NotNull ArrangementMatcherKey key) {
    switch (key) {
      case TYPE:
        return mySupportedTypes;
      case MODIFIER:
        return mySupportedModifiers;
    }
    // TODO den implement
    throw new IllegalArgumentException();
  }
}

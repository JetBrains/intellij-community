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

import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * Strategy that defines what subset of standard arrangement settings can be used during defining arrangement settings
 * and how they are organised (e.g. single settings can't have more than one visibility modifier for java language). 
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 2:26 PM
 */
public interface ArrangementStandardSettingsAware {

  /**
   * @return  settings to use by default, i.e. when a user hasn't been explicitly modified arrangement settings;
   *          <code>null</code> as an indication that no default settings are available
   */
  @Nullable
  StdArrangementSettings getDefaultSettings();

  /**
   * @return    <code>true</code> if name-based rules are supported by the current rearranger (it knows
   *            about {@link NameAwareArrangementEntry});
   *            <code>false</code> otherwise
   */
  boolean isNameFilterSupported();
  
  /**
   * Allows to answer if given entry type can be applied for the rule specified by the given settings node.
   * 
   * @param type     target entry type to check
   * @param current  holds information about current rule
   *                 (<code>null</code> to indicate a query if given entry type is supported in general)
   * @return         <code>true</code> if given entry type is supported; <code>false</code> otherwise
   */
  boolean isEnabled(@NotNull ArrangementEntryType type, @Nullable ArrangementMatchCondition current);

  /**
   * Allows to answer if given modifier can be applied for the rule specified by the given settings node.
   *
   * @param modifier target modifier to check
   * @param current  holds information about current rule
   *                 (<code>null</code> to indicate a query if given modifier is supported in general)
   * @return         <code>true</code> if given modifier is supported; <code>false</code> otherwise
   */
  boolean isEnabled(@NotNull ArrangementModifier modifier, @Nullable ArrangementMatchCondition current);


  /**
   * Allows to answer if given grouping type (optionally with the given order type) can be used.
   * 
   * @param groupingType  target grouping type to check
   * @param orderType     target order type to check whether it can be used with the given grouping type;
   *                      <code>null</code> to indicate that we want to know if given grouping type can be used in general
   *                      for the languages-specific realisation of this interface
   * @return              <code>true</code> if given arguments are eligible for the languages-specific realisation of this interface;
   *                      <code>false</code> otherwise
   */
  boolean isEnabled(@NotNull ArrangementGroupingType groupingType, @Nullable ArrangementEntryOrderType orderType);
  
  /**
   * @return    collections of mutual exclusion settings. E.g. not more than one visibility modifier can be used for a single
   *            java language rule
   */
  @NotNull
  Collection<Set<?>> getMutexes();
}

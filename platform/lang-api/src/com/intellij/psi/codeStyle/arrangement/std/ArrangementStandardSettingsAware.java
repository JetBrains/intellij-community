/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Defines a contract for {@link Rearranger} implementation which wants to use standard platform UI for configuring
 * and managing arrangement settings.
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
   * @return    ordered collection of grouping tokens eligible to use with the current rearranger.
   *            <b>Note:</b> platform code which uses this method caches returned results
   */
  @Nullable
  List<CompositeArrangementSettingsToken> getSupportedGroupingTokens();

  /**
   * @return    ordered collection of matching tokens eligible to use with the current rearranger
   *            <b>Note:</b> platform code which uses this method caches returned results
   */
  @Nullable
  List<CompositeArrangementSettingsToken> getSupportedMatchingTokens();

  /**
   * Allows to answer if given token is enabled in combination with other conditions specified by the given condition object.
   * <p/>
   * Example: say, current rearranger is for java and given condition is like 'public class'. This method is expected to
   * return <code>false</code> for token 'volatile' (because it can be applied only to fields) but <code>true</code>
   * for token 'abstract' (a java class can be abstract).
   * 
   * @param token    target token to check
   * @param current  an object which represents currently chosen tokens; <code>null</code> if no other token is selected
   * @return         <code>true</code> if given token is enabled with the given condition; <code>false</code> otherwise
   */
  boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current);

  /**
   * This method is assumed to be used only by third-party developers. All built-in IJ conditions are supposed
   * to be implemented in terms of {@link StdArrangementTokens}.
   * 
   * @param condition  target condition
   * @return           a matcher for the given condition
   * @throws IllegalArgumentException   if current rearranger doesn't know how to build a matcher from the given condition
   */
  @NotNull
  ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException;
  
  /**
   * @return    collections of mutual exclusion settings. It's is used by standard arrangement settings UI to automatically
   *            deselect elements on selection change. Example: 'private' modifier was selected. When any other modifier is selected
   *            'public' modifier is deselected if returned collection contains set of all supported visibility modifiers
   */
  @NotNull
  Collection<Set<ArrangementSettingsToken>> getMutexes();
}

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

import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Allows to inject custom logic for representation {@link ArrangementStandardSettingsAware standard settings-aware}
 * {@link Rearranger language-specific rearranger}.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/15/12 10:15 AM
 */
public interface ArrangementStandardSettingsRepresentationAware {

  /**
   * @param type    target entry type
   * @return        text to show end-user for the given type
   */
  @NotNull
  String getDisplayValue(@NotNull ArrangementEntryType type);

  /**
   * @param modifier  target modifier
   * @return          text to show end-user for the given modifier
   */
  @NotNull
  String getDisplayValue(@NotNull ArrangementModifier modifier);

  /**
   * @param groupingType  target grouping type
   * @return              text to show end-user for the given grouping type
   */
  @NotNull
  String getDisplayValue(@NotNull ArrangementGroupingType groupingType);

  /**
   * @param orderType  target order type
   * @return           text to show end-user for the given order type
   */
  @NotNull
  String getDisplayValue(@NotNull ArrangementEntryOrderType orderType);
  
  /**
   * Allows to sort given arrangement condition ids ('field', 'class', 'method', 'public', 'static', 'final' etc) before showing
   * them to an end-user.
   *
   * @param ids  target ids to use
   * @param <T>  id type
   * @return     sorted ids to use
   */
  @NotNull
  <T> List<T> sort(@NotNull Collection<T> ids);
}

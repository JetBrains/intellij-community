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
import com.intellij.psi.codeStyle.arrangement.sort.ArrangementEntrySortType;
import org.jetbrains.annotations.NotNull;

/**
 * // TODO den add doc
 * Strategy that defines what subset of standard arrangement settings can be used during defining arrangement settings. 
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 2:26 PM
 */
public interface ArrangementStandardSettingsAware {
  
  // TODO den add doc
  boolean isNameFilterEnabled(@NotNull ArrangementSettings settings);
  
  // TODO den add doc
  boolean isSupported(@NotNull ArrangementEntryType type);
  
  // TODO den add doc
  boolean isSupported(@NotNull ArrangementModifier modifier);

  // TODO den add doc
  boolean isSupported(@NotNull ArrangementEntrySortType type);
}

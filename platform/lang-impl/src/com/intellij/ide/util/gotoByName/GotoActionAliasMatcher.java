/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.GotoActionAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Allows {@link GotoActionAction} to find actions by synonyms.
 */
public interface GotoActionAliasMatcher {
  ExtensionPointName<GotoActionAliasMatcher> EP_NAME = ExtensionPointName.create("com.intellij.gotoActionAliasMatcher");

  /**
   * Returns true if the name argument is synonym for specified action.
   * @deprecated Use {@link #matchAction(AnAction, String)} instead
   */
  @Deprecated(forRemoval = true)
  default boolean match(@NotNull AnAction action, @NotNull String name) {
    return false;
  }

  default MatchMode matchAction(@NotNull AnAction action, @NotNull String pattern) {
    return match(action, pattern) ? MatchMode.NAME : MatchMode.NONE;
  }
}

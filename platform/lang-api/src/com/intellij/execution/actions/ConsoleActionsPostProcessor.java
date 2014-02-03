/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.actions;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for the {@link ConsoleView#createConsoleActions() console actions} customization.
 * 
 * @author Denis Zhdanov
 * @since 4/25/11 1:16 PM
 */
public abstract class ConsoleActionsPostProcessor {

  public static final ExtensionPointName<ConsoleActionsPostProcessor> EP_NAME = ExtensionPointName.create("com.intellij.consoleActionsPostProcessor");
  
  /**
   * Allows to adjust actions to use within the given console instance.
   * <p/>
   * <code>'Adjust'</code> here stands for 'add', 'remove', 'change order' etc.
   *
   * @param console     console instance which actions are being post-processed
   * @param actions     console actions that will be used by default
   * @return            actions to use within the given console instance (given actions may be returned by default)
   */
  @NotNull
  public AnAction[] postProcess(@NotNull ConsoleView console, @NotNull AnAction[] actions) {
    return actions;
  }

  @NotNull
  public AnAction[] postProcessPopupActions(@NotNull ConsoleView console, @NotNull AnAction[] actions) {
    return actions;
  }
}

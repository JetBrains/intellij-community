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
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Basic interface for requesting sending focus commands to {@code IdeFocusManager}
 */
public interface FocusRequestor extends Disposable {

  /**
   * Requests focus on a component
   * @param c - component to request focus to
   * @param forced - if true - focus request is explicit, must be fulfilled, if false - can be dropped
   * @return action callback that either notifies when the focus was obtained or focus request was dropped
   */
  @NotNull
  ActionCallback requestFocus(@NotNull Component c, boolean forced);

  /**
   * Runs a request focus command, actual focus request is defined by the user in the command itself
   * @param forced - if true - focus request is explicit, must be fulfilled, if false - can be dropped
   * @return action callback that either notifies when the focus was obtained or focus request was dropped
   */
  @NotNull
  ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced);
  
}

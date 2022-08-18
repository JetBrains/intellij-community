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
package com.intellij.openapi.actionSystem;

import org.intellij.lang.annotations.MagicConstant;

import java.awt.*;

public interface ActionButtonComponent {

  int NORMAL = 0;

  /**
   * The button is in hover state (mouse button is not pressed)
   */
  int POPPED = 1;

  /**
   * The button is pushed by mouse button and mouse cursor is over the button
   */
  int PUSHED = -1;

  /**
   * Can mean either the button is focused or the button is selected (like for toggleable buttons)
   */
  int SELECTED = 2;

  @MagicConstant(flags = {NORMAL, POPPED, PUSHED, SELECTED})
  @interface ButtonState { }

  @ButtonState
  int getPopState();

  int getWidth();

  int getHeight();

  Insets getInsets();
}

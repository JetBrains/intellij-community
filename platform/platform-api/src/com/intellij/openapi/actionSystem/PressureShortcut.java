/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.intellij.lang.annotations.JdkConstants;

import java.awt.event.MouseEvent;

public class PressureShortcut extends MouseShortcut {

  private double stage;

  public PressureShortcut(double stage) {
    super(MouseEvent.BUTTON1, 0, 1);
    this.stage = stage;
  }

  public int getButton() {
    return MouseEvent.BUTTON1;
  }

  @JdkConstants.InputEventMask
  public int getModifiers() {
    return 0;
  }

  public int getClickCount() {
    return 1;
  }

  public boolean equals(Object obj) {
    return obj instanceof PressureShortcut;
  }

  public int hashCode() {
    return super.hashCode() + (int)stage;
  }

  @Override
  public boolean isKeyboard() {
    return false;
  }

  @Override
  public boolean startsWith(final Shortcut sc) {
    return equals(sc);
  }

  @Override
  public String toString() {
    return "Force touch";
  }
}

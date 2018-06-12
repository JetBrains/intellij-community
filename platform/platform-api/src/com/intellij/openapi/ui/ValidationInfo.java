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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes why the data entered in a DialogWrapper is invalid.
 *
 * @author Konstantin Bulenkov
 * @see DialogWrapper#doValidate()
 */
public final class ValidationInfo {
  @NotNull
  public final String message;
  public final JComponent component;
  public final boolean disableOk;

  /**
   * Creates a validation error message associated with a specific component. The component will have an error icon drawn next to it,
   * and will be focused when the user tries to close the dialog by pressing OK.
   *
   * @param message   the error message to display.
   * @param component the component containing the invalid data.
   */
  public ValidationInfo(@NotNull String message, @Nullable JComponent component) {
    this(message, component, true);
  }

  /**
   * Creates a validation error message not associated with a specific component.
   *
   * @param message the error message to display.
   */
  public ValidationInfo(@NotNull String message) {
    this(message, null);
  }

  /**
   * Creates a validation error message not associated with a specific component.
   *
   * @param message   the error message to display.
   * @param disableOk whether to disable OK button.
   */
  public ValidationInfo(@NotNull String message, boolean disableOk) {
    this(message, null, disableOk);
  }

  /**
   * Creates a validation error message
   *
   * @param message   the error message to display.
   * @param component the component containing the invalid data.
   * @param disableOk whether to disable OK button.
   */
  private ValidationInfo(@NotNull String message, @Nullable JComponent component, boolean disableOk) {
    this.message = message;
    this.component = component;
    this.disableOk = disableOk;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ValidationInfo)) return false;

    ValidationInfo that = (ValidationInfo)o;
    return StringUtil.equals(this.message, that.message) &&
           this.component == that.component;
  }
}

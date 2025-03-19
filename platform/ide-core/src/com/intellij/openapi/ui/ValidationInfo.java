// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes why the data entered in a DialogWrapper is invalid.
 *
 * @author Konstantin Bulenkov
 * @see DialogWrapper#doValidate()
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/validation-errors.html">Validation errors guidelines</a>
 */
public final class ValidationInfo {
  /**
   * The description of a validation problem to display to user, in HTML format.
   * The blank message means that there is still a problem, but nothing to display.
   * It can be used for some obvious problems like an empty text field.
   */
  public final @NlsContexts.DialogMessage @NotNull String message;

  public final @Nullable JComponent component;

  public boolean okEnabled;
  public boolean warning;

  /**
   * Creates a validation error message associated with a specific component. The component will have an error icon drawn next to it,
   * and will be focused when the user tries to close the dialog by pressing OK.
   *
   * @param message   the error message to display.
   * @param component the component containing the invalid data.
   */
  public ValidationInfo(@NlsContexts.DialogMessage @NotNull String message, @Nullable JComponent component) {
    this.message = message;
    this.component = component;
  }

  /**
   * Creates a validation error message not associated with a specific component.
   *
   * @param message the error message to display.
   */
  public ValidationInfo(@NlsContexts.DialogMessage @NotNull String message) {
    this(message, null);
  }

  /**
   * Creates a validation error message associated with a specific component. The component will have an error icon drawn next to it,
   * and will be focused when the user tries to close the dialog by pressing OK.
   *
   * @param message   the error message to display.
   * @param component the component containing the invalid data.
   */
  public ValidationInfo(@NotNull HtmlChunk message, @Nullable JComponent component) {
    this.message = message.toString();
    this.component = component;
  }

  public ValidationInfo withOKEnabled() {
    okEnabled = true;
    return this;
  }

  public ValidationInfo asWarning() {
    warning = true;
    return this;
  }

  public ValidationInfo forComponent(@Nullable JComponent component) {
    ValidationInfo result = new ValidationInfo(message, component);
    result.warning = warning;
    result.okEnabled = okEnabled;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ValidationInfo that)) return false;

    return StringUtil.equals(this.message, that.message) &&
           this.component == that.component &&
           this.okEnabled == that.okEnabled &&
           this.warning == that.warning;
  }

  @Override
  public  String toString() {
    return "ValidationInfo{message='" + message + '\'' + '}';
  }
}

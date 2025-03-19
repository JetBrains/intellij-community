// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A macro which can be used in live templates.
 * <p>
 * Register in extension point {@code com.intellij.liveTemplateMacro}.
 */
public abstract class Macro {
  public static final ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateMacro");

  public abstract @NonNls String getName();

  /**
   * @return a presentable string that will be shown in the combobox in <em>Edit Template Variables</em> dialog
   * Default implementation returns a macro name with parentheses. Override it if parameters should be passed to the macro.
   */
  public @NlsSafe String getPresentableName() {
    return getName() + "()";
  }

  public @NonNls @NotNull String getDefaultValue() {
    return "";
  }

  public abstract @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context);

  public @Nullable Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return null;
  }

  public LookupElement @Nullable [] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    return null;
  }

  public boolean isAcceptableInContext(TemplateContextType context) {
    return true;
  }

  /**
   * @return focus degree to use for macro's lookup.
   * @see LookupFocusDegree
   */
  public @NotNull LookupFocusDegree getLookupFocusDegree() {
    return LookupFocusDegree.FOCUSED;
  }
}

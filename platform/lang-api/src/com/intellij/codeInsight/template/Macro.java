// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.openapi.extensions.ExtensionPointName;
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

  @NonNls
  public abstract String getName();

  /**
   * @return a presentable string that will be shown in the combobox in <em>Edit Template Variables</em> dialog
   */
  public abstract String getPresentableName();

  @NonNls
  @NotNull
  public String getDefaultValue() {
    return "";
  }

  @Nullable
  public abstract Result calculateResult(Expression @NotNull [] params, ExpressionContext context);

  @Nullable
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
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
  @NotNull
  public LookupFocusDegree getLookupFocusDegree() {
    return LookupFocusDegree.FOCUSED;
  }
}

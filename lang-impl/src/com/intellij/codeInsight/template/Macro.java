package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Macro {
  ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateMacro");
  
  @NonNls String getName();

  String getDescription ();

  @NonNls String getDefaultValue();

  @Nullable
  Result calculateResult(@NotNull Expression[] params, ExpressionContext context);

  @Nullable
  Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context);

  @Nullable
  LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context);
}

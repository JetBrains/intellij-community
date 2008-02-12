package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface Macro {
  ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateMacro");
  
  @NonNls String getName();

  String getDescription ();

  @NonNls String getDefaultValue();

  @Nullable
  Result calculateResult(Expression[] params, ExpressionContext context);

  @Nullable
  Result calculateQuickResult(Expression[] params, ExpressionContext context);

  @Nullable
  LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context);
}

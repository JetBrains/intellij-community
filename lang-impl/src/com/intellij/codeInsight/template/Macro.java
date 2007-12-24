package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

public interface Macro {
  ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateMacro");
  
  @NonNls String getName();

  String getDescription ();

  @NonNls String getDefaultValue();

  Result calculateResult(Expression[] params, ExpressionContext context);

  Result calculateQuickResult(Expression[] params, ExpressionContext context);

  LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context);
}

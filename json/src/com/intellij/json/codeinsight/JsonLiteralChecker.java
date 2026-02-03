// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface JsonLiteralChecker {
  ExtensionPointName<JsonLiteralChecker> EP_NAME = ExtensionPointName.create("com.intellij.json.jsonLiteralChecker");

  @Nullable
  @InspectionMessage String getErrorForNumericLiteral(String literalText);

  @Nullable
  Pair<TextRange, @InspectionMessage String> getErrorForStringFragment(Pair<TextRange, String> fragmentText, JsonStringLiteral stringLiteral);

  boolean isApplicable(PsiElement element);
}

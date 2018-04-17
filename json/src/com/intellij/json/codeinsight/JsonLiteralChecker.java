// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface JsonLiteralChecker {
  ExtensionPointName<JsonLiteralChecker> EP_NAME = ExtensionPointName.create("com.intellij.json.jsonLiteralChecker");

  @Nullable
  String getErrorForNumericLiteral(String literalText);

  @Nullable
  String getErrorForStringFragment(String fragmentText);

  boolean isApplicable(PsiElement element);
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.StringValidator;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A validator to check whether a given string is a valid Java identifier
 */
public class JavaIdentifierValidator implements StringValidator {
  @Override
  public @NotNull String validatorId() {
    return "javaIdentifier";
  }

  @Override
  public @Nullable String getErrorMessage(@Nullable Project project, @NotNull String string) {
    return StringUtil.isJavaIdentifier(string) ? null :
           JavaBundle.message("hint.text.not.valid.java.identifier");
  }
}

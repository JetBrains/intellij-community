// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validator that checks whether supplied string is a valid Java regular expression
 */
public final class RegexValidator implements StringValidator {
  @Override
  public @NotNull String validatorId() {
    return "regex";
  }

  @Override
  public @Nullable String getErrorMessage(@Nullable Project project, @NotNull String string) {
    try {
      Pattern.compile(string);
    }
    catch (PatternSyntaxException e) {
      return StringUtil.substringBefore(e.getMessage(), "\n");
    }
    return null;
  }
}

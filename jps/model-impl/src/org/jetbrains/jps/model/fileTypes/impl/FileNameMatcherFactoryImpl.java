// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.fileTypes.impl;

import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

public final class FileNameMatcherFactoryImpl extends FileNameMatcherFactory {
  @Override
  @NotNull
  public FileNameMatcher createMatcher(@NotNull String pattern) {
    if (pattern.startsWith("*.") &&
        pattern.indexOf('*', 2) < 0 &&
        pattern.indexOf('.', 2) < 0 &&
        pattern.indexOf('?', 2) < 0) {
      return new ExtensionFileNameMatcher(StringUtil.toLowerCase(pattern.substring(2)));
    }

    if (pattern.contains("*") || pattern.contains("?")) {
      return new WildcardFileNameMatcher(pattern);
    }

    return new ExactFileNameMatcher(pattern);
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class JavaChangeInfoConverters extends LanguageExtension<JavaChangeInfoConverter> {
  private static final JavaChangeInfoConverters INSTANCE = new JavaChangeInfoConverters();

  public JavaChangeInfoConverters() {
    super("com.intellij.java.changeSignature.converter");
  }

  @Nullable
  public static JavaChangeInfoConverter findConverter(Language language) {
    return INSTANCE.forLanguage(language);
  }

  @Nullable
  public static JavaChangeInfo getJavaChangeInfo(ChangeInfo changeInfo, UsageInfo usageInfo) {
    if (changeInfo instanceof JavaChangeInfo) {
      return (JavaChangeInfo)changeInfo;
    }
    else {
      JavaChangeInfoConverter converter = findConverter(changeInfo.getLanguage());
      if (converter != null) {
        return converter.toJavaChangeInfo(changeInfo, usageInfo);
      }
    }
    return null;
  }
}

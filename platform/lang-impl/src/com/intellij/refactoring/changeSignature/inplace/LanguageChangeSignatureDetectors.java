// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature.inplace;

import com.intellij.lang.LanguageExtension;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class LanguageChangeSignatureDetectors extends LanguageExtension<LanguageChangeSignatureDetector<ChangeInfo>> {
  public static final LanguageChangeSignatureDetectors INSTANCE = new LanguageChangeSignatureDetectors();

  LanguageChangeSignatureDetectors() {
    super("com.intellij.changeSignatureDetector");
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.signatureHelp;

import com.intellij.lang.LanguageExtension;

public final class LanguageSignatureHelp extends LanguageExtension<SignatureHelpProvider> {
  public static final LanguageSignatureHelp INSTANCE = new LanguageSignatureHelp();

  private LanguageSignatureHelp() {
    super("com.intellij.codeInsight.signatureHelp");
  }
}
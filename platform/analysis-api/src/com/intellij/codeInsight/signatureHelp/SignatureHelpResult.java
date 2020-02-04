// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.signatureHelp;

public final class SignatureHelpResult {

  private final int myActiveParameter;
  private final SignatureInfo[] mySignatures;

  public SignatureHelpResult(int activeParameter,
                             SignatureInfo[] signatures) {
    myActiveParameter = activeParameter;
    mySignatures = signatures;
  }

  public int getActiveParameter() {
    return myActiveParameter;
  }

  public SignatureInfo[] getSignatures() {
    return mySignatures;
  }
}

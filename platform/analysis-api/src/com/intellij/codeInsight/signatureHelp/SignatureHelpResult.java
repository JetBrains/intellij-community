// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.signatureHelp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SignatureHelpResult {

  private final List<SignatureInfo> mySignatures;

  public SignatureHelpResult(List<SignatureInfo> signatures) {
    mySignatures = Collections.unmodifiableList(new ArrayList<>(signatures));
  }

  public List<SignatureInfo> getSignatures() {
    return mySignatures;
  }
}

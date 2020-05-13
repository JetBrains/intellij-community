// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.signatureHelp;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * More primitive version of {@link com.intellij.lang.parameterInfo.ParameterInfoHandler} which is more suitable for
 * client-server protocol.
 */
public interface SignatureHelpProvider {

  @Nullable
  SignatureHelpResult getSignatureHelp(PsiFile file, int offset);
}

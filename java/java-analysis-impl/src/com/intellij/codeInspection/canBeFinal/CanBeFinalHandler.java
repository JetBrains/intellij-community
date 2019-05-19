// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.canBeFinal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMember;

public abstract class CanBeFinalHandler {
  public static final ExtensionPointName<CanBeFinalHandler> EP_NAME = ExtensionPointName.create("com.intellij.canBeFinal");

  public abstract boolean canBeFinal(PsiMember member);

  public static boolean allowToBeFinal(PsiMember member) {
    for (CanBeFinalHandler handler : EP_NAME.getExtensionList()) {
      if (!handler.canBeFinal(member)) return false;
    }
    return true;
  }
}

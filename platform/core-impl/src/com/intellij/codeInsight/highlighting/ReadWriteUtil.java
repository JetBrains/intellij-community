// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ReadWriteUtil {
  public static ReadWriteAccessDetector.Access getReadWriteAccess(@NotNull PsiElement[] primaryElements, @NotNull PsiElement element) {
    for (ReadWriteAccessDetector detector : ReadWriteAccessDetector.EP_NAME.getExtensionList()) {
      if (isReadWriteAccessibleElements(primaryElements, detector)) {
        return detector.getExpressionAccess(element);
      }
    }
    return null;
  }

  private static boolean isReadWriteAccessibleElements(@NotNull PsiElement[] primaryElements, @NotNull ReadWriteAccessDetector detector) {
    for (PsiElement element : primaryElements) {
      if (!detector.isReadWriteAccessible(element)) return false;
    }
    return true;
  }
}

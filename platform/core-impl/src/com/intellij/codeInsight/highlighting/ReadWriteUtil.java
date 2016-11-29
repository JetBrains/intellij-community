/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ReadWriteUtil {
  public static ReadWriteAccessDetector.Access getReadWriteAccess(@NotNull PsiElement[] primaryElements, @NotNull PsiElement element) {
    for (ReadWriteAccessDetector detector : Extensions.getExtensions(ReadWriteAccessDetector.EP_NAME)) {
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

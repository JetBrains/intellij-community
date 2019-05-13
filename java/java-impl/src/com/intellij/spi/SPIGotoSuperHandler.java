/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.spi;

import com.intellij.codeInsight.navigation.JavaGotoSuperHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spi.psi.SPIClassProviderReferenceElement;
import org.jetbrains.annotations.NotNull;

public class SPIGotoSuperHandler extends JavaGotoSuperHandler {
  @Override
  protected PsiElement getElement(@NotNull PsiFile file, int offset) {
    final SPIClassProviderReferenceElement
      providerElement = PsiTreeUtil.getParentOfType(super.getElement(file, offset), SPIClassProviderReferenceElement.class);
    if (providerElement != null) {
      return providerElement.resolve();
    }

    return null;
  }
}

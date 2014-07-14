/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion.originInfo;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.OriginInfoAwareElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides info for magic method or properties about their origin:
 * original class, rule according to which the element was added, etc
 *
 * @see OriginInfoProvider#provideOriginInfo(com.intellij.psi.PsiElement)
 * @see com.intellij.psi.OriginInfoAwareElement
 *
 *
 * @author Max Medvedev
 */
public abstract class OriginInfoProvider {
  private static final ExtensionPointName<OriginInfoProvider> EP_NAME = ExtensionPointName.create("com.intellij.originInfoProvider");

  @Nullable
  public static String getOriginInfo(@Nullable PsiElement element) {
    if (element == null) return null;
    return _getOriginInfo(element);
  }

  @Nullable
  private static String _getOriginInfo(@NotNull PsiElement element) {
    if (element instanceof OriginInfoAwareElement) {
      String info = ((OriginInfoAwareElement)element).getOriginInfo();
      if (info != null) return info;
    }


    for (OriginInfoProvider provider : EP_NAME.getExtensions()) {
      String info = provider.provideOriginInfo(element);
      if (info != null) return info;
    }

    return null;
  }

  /**
   * @param element magic element
   * @return info to show in completion tail text
   */
  @Nullable
  protected abstract String provideOriginInfo(@NotNull PsiElement element);
}

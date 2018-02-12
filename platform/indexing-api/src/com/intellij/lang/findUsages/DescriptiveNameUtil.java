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
package com.intellij.lang.findUsages;

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class DescriptiveNameUtil {

  @NotNull
  public static String getMetaDataName(@NotNull PsiMetaData metaData) {
    String name = metaData.getName();
    return StringUtil.isEmpty(name) ? "''" : name;
  }

  @NotNull
  public static String getDescriptiveName(@NotNull PsiElement psiElement) {
    PsiUtilCore.ensureValid(psiElement);

    if (psiElement instanceof PsiMetaOwner) {
      PsiMetaOwner psiMetaOwner = (PsiMetaOwner)psiElement;
      PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) return getMetaDataName(metaData);
    }

    if (psiElement instanceof PsiFile) {
      return ((PsiFile)psiElement).getName();
    }
    
    Language lang = psiElement.getLanguage();
    FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(lang);
    if (provider == null) {
      throw new AssertionError(lang);
    }
    return provider.getDescriptiveName(psiElement);
  }
}

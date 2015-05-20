/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class PsiPackageConverter extends Converter<PsiPackage> implements CustomReferenceConverter<PsiPackage> {
  public PsiPackage fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    return JavaPsiFacade.getInstance(context.getPsiManager().getProject()).findPackage(s);
  }

  public String toString(@Nullable PsiPackage psiPackage, final ConvertContext context) {
    return psiPackage == null ? null : psiPackage.getQualifiedName();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<PsiPackage> genericDomValue, PsiElement element, ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    GlobalSearchScope scope = context.getSearchScope();
    return new PackageReferenceSet(s, element, ElementManipulators.getOffsetInElement(element), scope != null? scope : GlobalSearchScope.allScope(element.getProject())).getPsiReferences();
  }
}

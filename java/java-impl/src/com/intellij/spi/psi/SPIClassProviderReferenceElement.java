/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spi.psi;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 */
public class SPIClassProviderReferenceElement extends SPIPackageOrClassReferenceElement {
  public SPIClassProviderReferenceElement(ASTNode node) {
    super(node);
  }

  @Override
  public TextRange getRangeInElement() {
    return TextRange.from(0, getTextLength());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final String name = getContainingFile().getName();
    final PsiClass superProvider = JavaPsiFacade.getInstance(getProject()).findClass(name, getResolveScope());
    if (superProvider != null) {
      final List<Object> result = new ArrayList<>();
      ClassInheritorsSearch.search(superProvider).forEach(psiClass -> {
        if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final String jvmClassName = ClassUtil.getJVMClassName(psiClass);
          if (jvmClassName != null) {
            result.add(LookupElementBuilder.create(psiClass, jvmClassName));
          }
        }
        return true;
      });
      return ArrayUtil.toObjectArray(result);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}

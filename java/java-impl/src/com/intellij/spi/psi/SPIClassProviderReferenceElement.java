// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spi.psi;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SPIClassProviderReferenceElement extends SPIPackageOrClassReferenceElement {
  public SPIClassProviderReferenceElement(ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return TextRange.from(0, getTextLength());
  }

  @Override
  public Object @NotNull [] getVariants() {
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
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }
}

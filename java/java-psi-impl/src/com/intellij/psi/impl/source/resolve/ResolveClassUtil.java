// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResolveClassUtil {
  public static @Nullable PsiClass resolveClass(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    if (ref instanceof PsiJavaCodeReferenceElementImpl &&
        ((PsiJavaCodeReferenceElementImpl)ref).getKindEnum(containingFile) == PsiJavaCodeReferenceElementImpl.Kind.CLASS_IN_QUALIFIED_NEW_KIND) {
      PsiNewExpression parent = PsiTreeUtil.getContextOfType(ref, PsiNewExpression.class);
      if (parent != null) {
        PsiExpression qualifier = parent.getQualifier();
        if (qualifier != null) {
          PsiType qualifierType = qualifier.getType();
          if (qualifierType instanceof PsiClassType) {
            PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifierType);
            if (qualifierClass != null) {
              return qualifierClass.findInnerClassByName(ref.getText(), true);
            }
          }
        }
      }
    }
    else {
      PsiElement classNameElement = ref.getReferenceNameElement();
      if (classNameElement instanceof PsiIdentifier) {
        String className = classNameElement.getText();
        ClassResolverProcessor processor = new ClassResolverProcessor(className, ref, containingFile);
        PsiScopesUtil.resolveAndWalk(processor, ref, null);
        if (processor.getResult().length == 1) {
          return (PsiClass)processor.getResult()[0].getElement();
        }
      }
    }

    return null;
  }
}
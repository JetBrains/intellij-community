/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResolveClassUtil {
  @Nullable
  public static PsiClass resolveClass(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    if (ref instanceof PsiJavaCodeReferenceElementImpl &&
        ((PsiJavaCodeReferenceElementImpl)ref).getKindEnum(containingFile) == PsiJavaCodeReferenceElementImpl.Kind.CLASS_IN_QUALIFIED_NEW_KIND) {
      PsiElement parent = ref.getParent();
      if (parent instanceof PsiAnonymousClass) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiNewExpression) {
        PsiExpression qualifier = ((PsiNewExpression)parent).getQualifier();
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
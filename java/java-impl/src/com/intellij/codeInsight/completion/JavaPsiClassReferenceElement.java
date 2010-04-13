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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaPsiClassReferenceElement extends LookupItem<Object> {             
  public static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new InsertHandler<JavaPsiClassReferenceElement>() {
    public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      if (completingRawConstructor(context, item)) {
        DefaultInsertHandler.NO_TAIL_HANDLER.handleInsert(context, item);
        ConstructorInsertHandler.insertParentheses(context, item, item.getObject());
      } else {
        new DefaultInsertHandler().handleInsert(context, item);
      }
    }

    private boolean completingRawConstructor(InsertionContext context, JavaPsiClassReferenceElement item) {
      final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
      final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
      if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
        PsiTypeParameter[] typeParameters = item.getObject().getTypeParameters();
        for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiExpression) prevElement.getParent(), true)) {
          final PsiType type = info.getType();

          if (info.isArrayTypeInfo()) {
            return false;
          }
          if (typeParameters.length > 0 && type instanceof PsiClassType && !((PsiClassType)type).isRaw()) {
            return false;
          }
        }
        return true;
      }

      return false;
    }
  };

  private final PsiAnchor myClass;
  private final String myQualifiedName;

  public JavaPsiClassReferenceElement(PsiClass psiClass) {
    super(psiClass.getName(), psiClass.getName());
    myClass = PsiAnchor.create(psiClass);
    myQualifiedName = psiClass.getQualifiedName();
    JavaCompletionUtil.setShowFQN(this);
    setInsertHandler(JAVA_CLASS_INSERT_HANDLER);
  }

  @NotNull
  @Override
  public PsiClass getObject() {
    return (PsiClass)myClass.retrieve();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaPsiClassReferenceElement)) return false;

    final JavaPsiClassReferenceElement that = (JavaPsiClassReferenceElement)o;

    return Comparing.equal(myQualifiedName, that.myQualifiedName);
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public int hashCode() {
    final String s = myQualifiedName;
    return s == null ? 239 : s.hashCode();
  }
}

/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author peter
 */
public class JavaPsiClassReferenceElement extends LookupItem<PsiClass> {
  public static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new InsertHandler<JavaPsiClassReferenceElement>() {
    public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      final PsiJavaCodeReferenceElement element =
          PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
      final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(element);
      if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
        ExpectedTypeInfo[] infos = ExpectedTypesProvider.getInstance(context.getProject()).getExpectedTypes((PsiExpression) prevElement.getParent(), true);
        boolean flag = true;
        PsiTypeParameter[] typeParameters = item.getObject().getTypeParameters();
        for (ExpectedTypeInfo info : infos) {
          final PsiType type = info.getType();

          if (info.isArrayTypeInfo()) {
            flag = false;
            break;
          }
          if (typeParameters.length > 0 && type instanceof PsiClassType) {
            if (!((PsiClassType)type).isRaw()) {
              flag = false;
            }
          }
        }
        if (flag) {
          item.setAttribute(LookupItem.NEW_OBJECT_ATTR, "");
          item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, ""); //strange hack
        }
      }
      new DefaultInsertHandler().handleInsert(context, item);
    }
  };
  private PsiClass myClass;

  public JavaPsiClassReferenceElement(PsiClass psiClass) {
    super(psiClass, psiClass.getName());
    JavaAwareCompletionData.setShowFQN(this);
    myClass = psiClass;
    setInsertHandler(JAVA_CLASS_INSERT_HANDLER);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaPsiClassReferenceElement)) return false;

    final JavaPsiClassReferenceElement that = (JavaPsiClassReferenceElement)o;

    return Comparing.equal(myClass.getQualifiedName(), that.myClass.getQualifiedName());
  }

  @Override
  public int hashCode() {
    final String s = myClass.getQualifiedName();
    return s == null ? 239 : s.hashCode();
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class LookupItemUtil {
  private static final Logger LOG = Logger.getInstance(LookupItemUtil.class);

  /**
   * @deprecated use {@link LookupElementBuilder}
   */
  @Deprecated
  @NotNull
  public static LookupElement objectToLookupItem(Object object) {
    if (object instanceof LookupElement) return (LookupElement)object;
    if (object instanceof PsiClass) {
      return JavaClassNameCompletionContributor.createClassLookupItem((PsiClass)object, true);
    }
    if (object instanceof PsiMethod) {
      return new JavaMethodCallElement((PsiMethod)object);
    }
    if (object instanceof PsiVariable) {
      return new VariableLookupItem((PsiVariable)object);
    }
    if (object instanceof PsiExpression) {
      return new ExpressionLookupItem((PsiExpression)object);
    }
    if (object instanceof PsiType) {
      return PsiTypeLookupItem.createLookupItem((PsiType)object, null);
    }
    if (object instanceof PsiPackage) {
      return new PackageLookupItem((PsiPackage)object);
    }

    String s = null;
    LookupItem item = new LookupItem(object, "");
    if (object instanceof PsiElement) {
      s = PsiUtilCore.getName((PsiElement)object);
    }
    TailType tailType = TailType.NONE;
    if (object instanceof PsiMetaData) {
      s = ((PsiMetaData)object).getName();
    }
    else if (object instanceof String) {
      s = (String)object;
    }
    else if (object instanceof PresentableLookupValue) {
      s = ((PresentableLookupValue)object).getPresentation();
    }

    if (s == null) {
      LOG.error("Null string for object: " + object + " of class " + (object != null ? object.getClass() : null));
    }
    item.setLookupString(s);

    item.setTailType(tailType);
    return item;
  }
}

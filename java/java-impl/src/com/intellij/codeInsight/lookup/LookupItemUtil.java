// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class LookupItemUtil {
  private static final Logger LOG = Logger.getInstance(LookupItemUtil.class);

  /**
   * @deprecated use {@link LookupElementBuilder}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull LookupElement objectToLookupItem(Object object) {
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

    LookupItem<Object> item = new LookupItem<>(object, "");
    TailType tailType = TailTypes.noneType();
    String s = switch (object) {
      case PsiElement element -> PsiUtilCore.getName(element);
      case PsiMetaData data -> data.getName();
      case String string -> string;
      case PresentableLookupValue value -> value.getPresentation();
      case null, default -> null;
    };

    if (s == null) {
      LOG.error("Null string for object: " + object + " of class " + (object != null ? object.getClass() : null));
    }
    item.setLookupString(s);

    item.setTailType(tailType);
    return item;
  }
}

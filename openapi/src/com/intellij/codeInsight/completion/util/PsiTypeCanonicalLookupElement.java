/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiTypeCanonicalLookupElement extends LookupElement {
  private static final LookupElementRenderer<PsiTypeCanonicalLookupElement> RENDERER = new LookupElementRenderer<PsiTypeCanonicalLookupElement>() {
    public void renderElement(final PsiTypeCanonicalLookupElement element, final LookupElementPresentation presentation) {
      final PsiClass psiClass = element.getPsiClass();
      if (psiClass != null) {
        presentation.setIcon(psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY));
        presentation.setTailText(" (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")", true, false, false);
      }
      final PsiType type = element.getPsiType();
      presentation.setItemText(type.getPresentableText(), false, type instanceof PsiPrimitiveType);
    }
  };
  private final PsiType myType;
  private final String myPresentableText;

  public PsiTypeCanonicalLookupElement(@NotNull final PsiType type) {
    myType = type;
    myPresentableText = myType.getPresentableText();
  }

  @NotNull
  @Override
  public Object getObject() {
    final PsiClass psiClass = getPsiClass();
    if (psiClass != null) {
      return psiClass;
    }
    return super.getObject();
  }

  @Nullable
  public PsiClass getPsiClass() {
    return PsiUtil.resolveClassInType(myType);
  }

  public PsiType getPsiType() {
    return myType;
  }

  @NotNull
  public String getLookupString() {
    return myPresentableText;
  }

  public InsertHandler<PsiTypeCanonicalLookupElement> getInsertHandler() {
    return new InsertHandler<PsiTypeCanonicalLookupElement>() {
      public void handleInsert(final InsertionContext context, final PsiTypeCanonicalLookupElement item) {
        context.getEditor().getDocument().replaceString(context.getStartOffset(), context.getStartOffset() + item.getLookupString().length(), item.getPsiType().getCanonicalText());
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiTypeCanonicalLookupElement)) return false;

    final PsiTypeCanonicalLookupElement that = (PsiTypeCanonicalLookupElement)o;

    if (!myType.equals(that.myType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myType.hashCode();
  }

  @NotNull
  protected LookupElementRenderer<PsiTypeCanonicalLookupElement> getRenderer() {
    return RENDERER;
  }
}

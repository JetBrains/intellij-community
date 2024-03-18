// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PackageLookupItem extends LookupElement {
  private final PsiPackage myPackage;
  private final String myString;
  private final boolean myAddDot;

  public PackageLookupItem(@NotNull PsiPackage aPackage) {
    this(aPackage, null);
  }

  public PackageLookupItem(@NotNull PsiPackage pkg, @Nullable PsiElement context) {
    myPackage = pkg;
    myString = StringUtil.notNullize(myPackage.getName());

    PsiFile file = context == null ? null : context.getContainingFile();
    boolean inExportsOpens = context != null && context.getParent() instanceof PsiPackageAccessibilityStatement;
    myAddDot = !inExportsOpens &&
               (!(file instanceof PsiJavaCodeReferenceCodeFragment) || ((PsiJavaCodeReferenceCodeFragment)file).isClassesAccepted());
  }

  @NotNull
  @Override
  public Object getObject() {
    return myPackage;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myString;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    if (myAddDot) {
      presentation.setItemText(myString + ".");
    }
    presentation.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Package));
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    if (myAddDot) {
      context.setAddCompletionChar(false);
      TailTypes.dotType().processTail(context.getEditor(), context.getTailOffset());
    }
    if (myAddDot || context.getCompletionChar() == '.') {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;

public abstract class UnresolvableCollisionUsageInfo extends CollisionUsageInfo {
  public UnresolvableCollisionUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  /**
   * @return user-readable HTML description of the conflict
   */
  public abstract @NlsContexts.DialogMessage String getDescription();

  /**
   * @return possibly shorter text (no HTML) description of the conflict which could be used as a popup title
   */
  public @NlsContexts.PopupTitle String getShortDescription() {
    return StringUtil.stripHtml(getDescription(), false);
  }
}

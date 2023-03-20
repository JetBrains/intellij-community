// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;

import java.awt.event.MouseEvent;

public abstract class InheritorsLineMarkerNavigator extends LineMarkerNavigator implements GutterIconNavigationHandler<PsiElement> {
  @Override
  public final void browse(MouseEvent e, PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null) return;
    new GotoImplementationHandler().navigateToImplementations(parent, e, getMessageForDumbMode());
  }

  @Override
  public void navigate(MouseEvent e, PsiElement elt) {
    browse(e, elt);
  }

  protected abstract @NlsContexts.PopupContent String getMessageForDumbMode();
}

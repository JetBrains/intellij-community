// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class ClassInitializerTreeElement extends PsiTreeElementBase<PsiClassInitializer> implements AccessLevelProvider{
  public ClassInitializerTreeElement(PsiClassInitializer initializer) {
    super(initializer);
  }

  @Override
  public String getPresentableText() {
    PsiClassInitializer initializer = getElement();
    assert initializer != null;
    String isStatic = initializer.hasModifierProperty(PsiModifier.STATIC) ? PsiModifier.STATIC + " " : "";
    return JavaStructureViewBundle.message("static.class.initializer", isStatic);
  }

  @Override
  public String getLocationString() {
    PsiClassInitializer initializer = getElement();
    assert initializer != null;
    PsiCodeBlock body = initializer.getBody();
    PsiElement first = body.getFirstBodyElement();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    if (first == body.getRBrace()) first = null;
    if (first != null) {
      return StringUtil.first(first.getText(), 20, true);
    }
    return null;
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }

  @Override
  public int getAccessLevel() {
    return PsiUtil.ACCESS_LEVEL_PRIVATE;
  }

  @Override
  public int getSubLevel() {
    return 0;
  }
}

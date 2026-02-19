// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class PsiFieldTreeElement extends JavaVariableBaseTreeElement<PsiField> {
  public PsiFieldTreeElement(PsiField field, boolean isInherited) {
    super(isInherited,field);
 }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    PsiField field = getField();
    if (field instanceof PsiEnumConstant) {
      PsiEnumConstantInitializer initializingClass = ((PsiEnumConstant)field).getInitializingClass();
      if (initializingClass != null) {
        return JavaClassTreeElement.getClassChildren(initializingClass);
      }
    }
    return Collections.emptyList();
  }

  public PsiField getField() {
    return getElement();
  }
}

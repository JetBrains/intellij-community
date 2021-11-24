// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class PsiFieldTreeElement extends JavaVariableBaseTreeElement<PsiField> {
  public PsiFieldTreeElement(PsiField field, boolean isInherited) {
    super(isInherited,field);
 }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
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

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.util.JavaImplicitClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class JavaFileTreeElement extends PsiTreeElementBase<PsiClassOwner> implements ItemPresentation {
  public JavaFileTreeElement(PsiClassOwner file) {
    super(file);
  }

  @Override
  public String getPresentableText() {
    PsiClassOwner element = getElement();
    return element == null ? "" : element.getName();
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    PsiClassOwner element = getElement();
    if (element == null) return Collections.emptyList();
    PsiClass[] classes = element.getClasses();
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(element);
    if (implicitClass != null) {
      return JavaClassTreeElement.getClassChildren(implicitClass);
    }

    ArrayList<StructureViewTreeElement> result = new ArrayList<>();
    for (PsiClass aClass : classes) {
      result.add(new JavaClassTreeElement(aClass, false));
    }
    return result;

  }
}

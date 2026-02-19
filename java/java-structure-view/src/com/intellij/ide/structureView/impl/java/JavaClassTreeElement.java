// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {

  public JavaClassTreeElement(PsiClass cls, boolean inherited) {
    super(inherited, cls);
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return getClassChildren(getElement());
  }

  static Collection<StructureViewTreeElement> getClassChildren(PsiClass aClass) {
    if (aClass == null) return Collections.emptyList();

    List<PsiElement> members = new ArrayList<>(getOwnChildren(aClass));
    members.sort(PsiElementOrderComparator.getInstance());
    List<StructureViewTreeElement> children = new ArrayList<>(members.size());

    for (PsiElement child : members) {
      if (!child.isValid()) continue;
      switch (child) {
        case PsiClass c -> children.add(new JavaClassTreeElement(c, false));
        case PsiField f -> children.add(new PsiFieldTreeElement(f, false));
        case PsiMethod m -> children.add(new PsiMethodTreeElement(m, false));
        case PsiClassInitializer i -> children.add(new ClassInitializerTreeElement(i));
        default -> {}
      }
    }
    PsiRecordHeader header = aClass.getRecordHeader();
    if (header != null) {
      for (PsiRecordComponent recordComponent : header.getRecordComponents()) {
        children.add(new JavaRecordComponentTreeElement(recordComponent, false));
      }
    }
    return children;
  }

  static @NotNull Set<PsiElement> getOwnChildren(@NotNull PsiClass aClass) {
    HashSet<PsiElement> members = new HashSet<>();
    addPhysicalElements(aClass.getFields(), members, aClass);
    addPhysicalElements(aClass.getMethods(), members, aClass);
    addPhysicalElements(aClass.getInnerClasses(), members, aClass);
    addPhysicalElements(aClass.getInitializers(), members, aClass);
    return members;
  }

  private static void addPhysicalElements(PsiMember @NotNull [] elements, @NotNull Collection<? super PsiElement> to, @NotNull PsiClass aClass) {
    for (PsiMember element : elements) {
      PsiElement mirror = PsiImplUtil.handleMirror(element);
      if (mirror instanceof LightElement) continue;
      if (mirror instanceof PsiMember member && aClass.equals(member.getContainingClass())) {
        to.add(mirror);
      }
    }
  }

  @Override
  public String getPresentableText() {
    PsiClass o = getElement();
    return o == null ? "" : o.getName();
  }

  @Override
  public boolean isPublic() {
    PsiClass o = getElement();
    return o != null && o.getParent() instanceof PsiFile || super.isPublic();
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {

  public JavaClassTreeElement(PsiClass cls, boolean inherited) {
    super(inherited, cls);
  }

  /**
   * @deprecated use {@link #JavaClassTreeElement(PsiClass, boolean)}
   * @noinspection unused
   */
  @Deprecated(forRemoval = true)
  public JavaClassTreeElement(PsiClass cls, boolean inherited, Set<PsiClass> parents) {
    this(cls, inherited);
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return getClassChildren(getElement());
  }

  static Collection<StructureViewTreeElement> getClassChildren(PsiClass aClass) {
    if (aClass == null) return Collections.emptyList();

    LinkedHashSet<PsiElement> members = getOwnChildren(aClass);
    List<StructureViewTreeElement> children = new ArrayList<>(members.size());

    for (PsiElement child : members) {
      if (!child.isValid()) continue;
      if (child instanceof PsiClass) {
        children.add(new JavaClassTreeElement((PsiClass)child, false));
      }
      else if (child instanceof PsiField) {
        children.add(new PsiFieldTreeElement((PsiField)child, false));
      }
      else if (child instanceof PsiMethod) {
        children.add(new PsiMethodTreeElement((PsiMethod)child, false));
      }
      else if (child instanceof PsiClassInitializer) {
        children.add(new ClassInitializerTreeElement((PsiClassInitializer)child));
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

  static LinkedHashSet<PsiElement> getOwnChildren(@NotNull PsiClass aClass) {
    return SlowOperations.allowSlowOperations(() -> doGetOwnChildren(aClass));
  }

  private static @NotNull LinkedHashSet<PsiElement> doGetOwnChildren(@NotNull PsiClass aClass) {
    LinkedHashSet<PsiElement> members = new LinkedHashSet<>();
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
      if (mirror instanceof PsiMember && aClass.equals(((PsiMember)mirror).getContainingClass())) {
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

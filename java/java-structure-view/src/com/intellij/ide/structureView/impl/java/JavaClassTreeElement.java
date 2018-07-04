/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {

  public JavaClassTreeElement(PsiClass cls, boolean inherited) {
    super(inherited, cls);
  }

  /** @noinspection unused*/
  @Deprecated
  public JavaClassTreeElement(PsiClass cls, boolean inherited, Set<PsiClass> parents) {
    this(cls, inherited);
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return getClassChildren();
  }

  private Collection<StructureViewTreeElement> getClassChildren() {
    final PsiClass aClass = getElement();
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
    return children;
  }

  static LinkedHashSet<PsiElement> getOwnChildren(@NotNull PsiClass aClass) {
    LinkedHashSet<PsiElement> members = new LinkedHashSet<>();
    addPhysicalElements(aClass.getFields(), members, aClass);
    addPhysicalElements(aClass.getMethods(), members, aClass);
    addPhysicalElements(aClass.getInnerClasses(), members, aClass);
    addPhysicalElements(aClass.getInitializers(), members, aClass);
    return members;
  }

  private static void addPhysicalElements(@NotNull PsiMember[] elements, @NotNull Collection<PsiElement> to, @NotNull PsiClass aClass) {
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

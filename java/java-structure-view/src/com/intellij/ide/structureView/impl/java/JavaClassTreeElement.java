/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  private final Set<PsiClass> myParents;

  public JavaClassTreeElement(PsiClass cls, boolean inherited, Set<PsiClass> parents) {
    super(inherited, cls);
    myParents = parents;
    myParents.add(cls);
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

    //aClass.processDeclarations(new AddAllMembersProcessor(inherited, aClass), ResolveState.initial(), null, aClass);

    for (PsiElement child : members) {
      if (!child.isValid()) continue;
      if (child instanceof PsiClass && !myParents.contains((PsiClass)child)) {
        children.add(new JavaClassTreeElement((PsiClass)child, false, myParents));
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

  static LinkedHashSet<PsiElement> getOwnChildren(PsiClass aClass) {
    LinkedHashSet<PsiElement> members = new LinkedHashSet<>();
    addPhysicalElements(aClass.getFields(), members);
    addPhysicalElements(aClass.getMethods(), members);
    addPhysicalElements(aClass.getInnerClasses(), members);
    addPhysicalElements(aClass.getInitializers(), members);
    return members;
  }

  private static void addPhysicalElements(PsiElement[] elements, LinkedHashSet<PsiElement> to) {
    for (PsiElement element : elements) {
      PsiElement mirror = PsiImplUtil.handleMirror(element);
      if (!(mirror instanceof LightElement)) {
        to.add(mirror);
      }
    }
  }

  public Set<PsiClass> getParents() {
    return myParents;
  }

  @Override
  public String getPresentableText() {
    return getElement().getName();
  }

  @Override
  public boolean isPublic() {
    return getElement().getParent() instanceof PsiFile || super.isPublic();
  }
}

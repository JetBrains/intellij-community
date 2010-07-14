/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {
  public JavaClassTreeElement(PsiClass aClass, boolean inherited) {
    super(inherited,aClass);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return getClassChildren();
  }

  private Collection<StructureViewTreeElement> getClassChildren() {
    ArrayList<StructureViewTreeElement> array = new ArrayList<StructureViewTreeElement>();

    final PsiClass aClass = getElement();
    if (aClass == null) return array;

    List<PsiElement> children = Arrays.asList(aClass.getChildren());
    Collection<PsiElement> ownChildren = new THashSet<PsiElement>();
    ContainerUtil.addAll(ownChildren, aClass.getFields());
    ContainerUtil.addAll(ownChildren, aClass.getMethods());
    ContainerUtil.addAll(ownChildren, aClass.getInnerClasses());
    ContainerUtil.addAll(ownChildren, aClass.getInitializers());
    Collection<PsiElement> inherited = new LinkedHashSet<PsiElement>(children);

    aClass.processDeclarations(new AddAllMembersProcessor(inherited, aClass), ResolveState.initial(), null, aClass);

    for (PsiElement child : inherited) {
      if (!child.isValid()) continue;
      boolean isInherited = !ownChildren.contains(child);
      if (child instanceof PsiClass) {
        array.add(new JavaClassTreeElement((PsiClass)child, isInherited));
      }
      else if (child instanceof PsiField) {
        array.add(new PsiFieldTreeElement((PsiField)child, isInherited));
      }
      else if (child instanceof PsiMethod) {
        array.add(new PsiMethodTreeElement((PsiMethod)child, isInherited));
      }
      else if (child instanceof PsiClassInitializer) {
        array.add(new ClassInitializerTreeElement((PsiClassInitializer)child));
      }
    }
    return array;
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  public boolean isPublic() {
    return getElement().getParent() instanceof PsiFile || super.isPublic();
  }
}

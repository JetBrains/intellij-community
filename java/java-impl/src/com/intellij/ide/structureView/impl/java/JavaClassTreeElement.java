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
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {
  public JavaClassTreeElement(PsiClass cls, boolean inherited) {
    super(inherited, cls);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return getClassChildren();
  }

  private Collection<StructureViewTreeElement> getClassChildren() {
    final PsiClass aClass = getElement();
    if (aClass == null) return Collections.emptyList();

    Collection<PsiElement> members = new THashSet<PsiElement>();
    ContainerUtil.addAll(members, aClass.getFields());
    ContainerUtil.addAll(members, aClass.getMethods());
    ContainerUtil.addAll(members, aClass.getInnerClasses());
    ContainerUtil.addAll(members, aClass.getInitializers());
    List<StructureViewTreeElement> children = new ArrayList<StructureViewTreeElement>(members.size());

    //aClass.processDeclarations(new AddAllMembersProcessor(inherited, aClass), ResolveState.initial(), null, aClass);

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

  public String getPresentableText() {
    return getElement().getName();
  }

  public boolean isPublic() {
    return getElement().getParent() instanceof PsiFile || super.isPublic();
  }
}

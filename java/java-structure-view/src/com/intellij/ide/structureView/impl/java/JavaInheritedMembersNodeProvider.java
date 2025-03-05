// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaInheritedMembersNodeProvider extends InheritedMembersNodeProvider {
  @Override
  public @NotNull Collection<TreeElement> provideNodes(@NotNull TreeElement node) {
    if (!(node instanceof JavaClassTreeElement classNode)) return Collections.emptyList();

    final PsiClass aClass = classNode.getElement();
    if (aClass == null) return Collections.emptyList();

    Collection<PsiElement> inherited = new LinkedHashSet<>();
    Collection<PsiElement> ownChildren = JavaClassTreeElement.getOwnChildren(aClass);

    aClass.processDeclarations(new AddAllMembersProcessor(inherited, aClass), ResolveState.initial(), null, aClass);
    inherited.removeAll(ownChildren);
    if (aClass instanceof PsiAnonymousClass) {
      final PsiElement element = ((PsiAnonymousClass)aClass).getBaseClassReference().resolve();
      if (element instanceof PsiClass) {
        ContainerUtil.addAll(inherited, ((PsiClass)element).getInnerClasses());
      }
    }
    List<TreeElement> array = new ArrayList<>();
    for (PsiElement child : inherited) {
      if (!child.isValid()) continue;
      if (child instanceof PsiClass) {
        array.add(new JavaClassTreeElement((PsiClass)child, true));
      }
      else if (child instanceof PsiField) {
        array.add(new PsiFieldTreeElement((PsiField)child, true));
      }
      else if (child instanceof PsiMethod) {
        array.add(new PsiMethodTreeElement((PsiMethod)child, true));
      }
      else if (child instanceof PsiClassInitializer) {
        array.add(new ClassInitializerTreeElement((PsiClassInitializer)child));
      }
    }
    return array;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SuperTypesGrouper implements Grouper{
  public static final Key<WeakReference<PsiMethod>> SUPER_METHOD_KEY = Key.create("StructureTreeBuilder.SUPER_METHOD_KEY");
  public static final @NonNls String ID = "SHOW_INTERFACES";

  @Override
  public @NotNull Collection<Group> group(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<TreeElement> children) {
    if (isParentGrouped(parent)) return Collections.emptyList();
    Map<Group, SuperTypeGroup> groups = new HashMap<>();

    for (TreeElement child : children) {
      if (child instanceof PsiMethodTreeElement element) {
        PsiMethod method = element.getMethod();
        if (element.isInherited()) {
          PsiClass groupClass = method.getContainingClass();
          final SuperTypeGroup group = getOrCreateGroup(groupClass, SuperTypeGroup.OwnershipType.INHERITS, groups);
          group.addMethod(child);
        }
        else {
          PsiMethod[] superMethods = method.findSuperMethods();

          if (superMethods.length > 0) {
            //prefer interface, if there are any
            for (int i = 1; i < superMethods.length; i++) {
              PsiMethod superMethod = superMethods[i];
              PsiClass containingClass = superMethod.getContainingClass();
              if (containingClass != null && containingClass.isInterface()) {
                ArrayUtil.swap(superMethods, 0, i);
                break;
              }
            }

            PsiMethod superMethod = superMethods[0];
            method.putUserData(SUPER_METHOD_KEY, new WeakReference<>(superMethod));
            PsiClass groupClass = superMethod.getContainingClass();
            boolean overrides = methodOverridesSuper(method, superMethod);
            final SuperTypeGroup.OwnershipType ownershipType =
              overrides ? SuperTypeGroup.OwnershipType.OVERRIDES : SuperTypeGroup.OwnershipType.IMPLEMENTS;
            SuperTypeGroup group = getOrCreateGroup(groupClass, ownershipType, groups);
            group.addMethod(child);
          }
        }
      }
    }
    return groups.keySet();
  }

  private static SuperTypeGroup getOrCreateGroup(final PsiClass groupClass, final SuperTypeGroup.OwnershipType ownershipType, final Map<Group, SuperTypeGroup> groups) {
    SuperTypeGroup superTypeGroup =
      new SuperTypeGroup(groupClass, ownershipType);
    SuperTypeGroup existing = groups.get(superTypeGroup);
    if (existing == null) {
      groups.put(superTypeGroup, superTypeGroup);
      existing = superTypeGroup;
    }
    return existing;
  }

  private static boolean isParentGrouped(AbstractTreeNode<?> parent) {
    while (parent != null) {
      if (parent.getValue() instanceof SuperTypeGroup) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static boolean methodOverridesSuper(PsiMethod method, PsiMethod superMethod) {
    boolean overrides = false;
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) || !superMethod.hasModifierProperty(PsiModifier.ABSTRACT)){
      overrides = true;
    }
    return overrides;

  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(StructureViewBundle.message("action.structureview.group.methods.by.defining.type"), null,
                                      AllIcons.General.ImplementingMethod);
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }

}

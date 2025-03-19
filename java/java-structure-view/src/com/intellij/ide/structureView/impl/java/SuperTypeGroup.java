// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class SuperTypeGroup implements Group, ItemPresentation, AccessLevelProvider{
  private final SmartPsiElementPointer mySuperClassPointer;
  private final OwnershipType myOverrides;
  private final Collection<TreeElement> myChildren = new ArrayList<>();

  public enum OwnershipType {
    IMPLEMENTS,
    OVERRIDES,
    INHERITS
  }

  public SuperTypeGroup(PsiClass superClass, OwnershipType type) {
    myOverrides = type;
    mySuperClassPointer = SmartPointerManager.getInstance(superClass.getProject()).createSmartPsiElementPointer(superClass);
  }

  @Override
  public @NotNull Collection<TreeElement> getChildren() {
    return myChildren;
  }

  private @Nullable PsiClass getSuperClass() {
    return (PsiClass)mySuperClassPointer.getElement();
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public Icon getIcon(boolean open) {
    return switch (myOverrides) {
      case IMPLEMENTS -> AllIcons.General.ImplementingMethod;
      case INHERITS -> AllIcons.General.InheritedMethod;
      case OVERRIDES -> AllIcons.General.OverridingMethod;
    };
  }

  @Override
  public String getPresentableText() {
    return toString();
  }

  @Override
  public String toString() {
    final PsiClass superClass = getSuperClass();
    return superClass != null ? superClass.getName() : StructureViewBundle.message("node.structureview.invalid");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof SuperTypeGroup superTypeGroup &&
           myOverrides == superTypeGroup.myOverrides &&
           Objects.equals(getSuperClass(), superTypeGroup.getSuperClass());
  }

  @Override
  public int hashCode() {
    final PsiClass superClass = getSuperClass();
    return superClass  != null ? superClass .hashCode() : 0;
  }

  public Object getValue() {
    return this;
  }

  @Override
  public int getAccessLevel() {
    final PsiClass superClass = getSuperClass();
    PsiModifierList modifierList = superClass == null ? null : superClass.getModifierList();
    return modifierList == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList);
  }

  @Override
  public int getSubLevel() {
    return 1;
  }

  public void addMethod(final TreeElement superMethod) {
     myChildren.add(superMethod);
  }
}

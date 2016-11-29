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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
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

public class SuperTypeGroup implements Group, ItemPresentation, AccessLevelProvider{
  private final SmartPsiElementPointer mySuperClassPointer;
  private final OwnershipType myOverrides;
  private final Collection<TreeElement> myChildren = new ArrayList<>();

  public static enum OwnershipType {
    IMPLEMENTS,
    OVERRIDES,
    INHERITS
  }

  public SuperTypeGroup(PsiClass superClass, OwnershipType type) {
    myOverrides = type;
    mySuperClassPointer = SmartPointerManager.getInstance(superClass.getProject()).createSmartPsiElementPointer(superClass);
  }

  @Override
  @NotNull
  public Collection<TreeElement> getChildren() {
    return myChildren;
  }

  @Nullable
  private PsiClass getSuperClass() {
    return (PsiClass)mySuperClassPointer.getElement();
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public Icon getIcon(boolean open) {
    switch (myOverrides) {
      case IMPLEMENTS:
        return AllIcons.General.ImplementingMethod;
      case INHERITS:
        return AllIcons.General.InheritedMethod;
      case OVERRIDES:
        return AllIcons.General.OverridingMethod;
    }

    return null; // Can't be
  }

  @Override
  public String getLocationString() {
    return null;
  }

  @Override
  public String getPresentableText() {
    return toString();
  }

  public String toString() {
    final PsiClass superClass = getSuperClass();
    return superClass != null ? superClass.getName() : IdeBundle.message("node.structureview.invalid");
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuperTypeGroup)) return false;

    final SuperTypeGroup superTypeGroup = (SuperTypeGroup)o;

    if (myOverrides != superTypeGroup.myOverrides) return false;
    final PsiClass superClass = getSuperClass();
    if (superClass != null ? !superClass .equals(superTypeGroup.getSuperClass() ) : superTypeGroup.getSuperClass()  != null) return false;

    return true;
  }

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

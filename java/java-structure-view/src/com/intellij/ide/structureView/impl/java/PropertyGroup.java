// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyAccessorDetector;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public final class PropertyGroup implements Group, ColoredItemPresentation, AccessLevelProvider, WeighedItem {
  @NotNull private final String myPropertyName;
  @NotNull private final String myTypeText;

  private SmartPsiElementPointer<?> myFieldPointer;
  private SmartPsiElementPointer<?> myGetterPointer;
  private SmartPsiElementPointer<?> mySetterPointer;
  private boolean myIsStatic;

  public static final Icon PROPERTY_READ_ICON = AllIcons.Nodes.PropertyRead;
  public static final Icon PROPERTY_READ_STATIC_ICON = AllIcons.Nodes.PropertyReadStatic;
  public static final Icon PROPERTY_WRITE_ICON = AllIcons.Nodes.PropertyWrite;
  public static final Icon PROPERTY_WRITE_STATIC_ICON = AllIcons.Nodes.PropertyWriteStatic;
  public static final Icon PROPERTY_READ_WRITE_ICON = AllIcons.Nodes.PropertyReadWrite;
  public static final Icon PROPERTY_READ_WRITE_STATIC_ICON = AllIcons.Nodes.PropertyReadWriteStatic;

  private final Project myProject;
  private final Collection<TreeElement> myChildren = new ArrayList<>();

  private PropertyGroup(@NotNull String propertyName, @NotNull PsiType propertyType, boolean isStatic, @NotNull Project project) {
    myPropertyName = propertyName;
    myTypeText = propertyType.getPresentableText();
    myIsStatic = isStatic;
    myProject = project;
  }

  public static PropertyGroup createOn(PsiElement object, final TreeElement treeElement) {
    if (object instanceof PsiField field) {
      PropertyGroup group = new PropertyGroup(PropertyUtilBase.suggestPropertyName(field), field.getType(),
                                              field.hasModifierProperty(PsiModifier.STATIC), field.getProject());
      group.setField(field);
      group.myChildren.add(treeElement);
      return group;
    }
    else if (object instanceof PsiMethod method) {
      final PropertyAccessorDetector.PropertyAccessorInfo accessorInfo = PropertyAccessorDetector.detectFrom(method);
      if (null != accessorInfo &&
          (accessorInfo.isKindOf(PropertyKind.GETTER) || accessorInfo.isKindOf(PropertyKind.SETTER))) {

        PropertyGroup group = new PropertyGroup(accessorInfo.getPropertyName(), accessorInfo.getPropertyType(),
                                                method.hasModifierProperty(PsiModifier.STATIC), method.getProject());
        if (accessorInfo.isKindOf(PropertyKind.GETTER)) {
          group.setGetter(method);
        }
        else {
          group.setSetter(method);
        }
        group.myChildren.add(treeElement);
        return group;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<TreeElement> getChildren() {
    return myChildren;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public Icon getIcon(boolean open) {
    if (isStatic()) {
      if (getGetter() != null && getSetter() != null) {
        return PROPERTY_READ_WRITE_STATIC_ICON;
      }
      else if (getGetter() != null) {
        return PROPERTY_READ_STATIC_ICON;
      }
      else {
        return PROPERTY_WRITE_STATIC_ICON;
      }
    }
    else {
      if (getGetter() != null && getSetter() != null) {
        return PROPERTY_READ_WRITE_ICON;
      }
      else if (getGetter() != null) {
        return PROPERTY_READ_ICON;
      }
      else {
        return PROPERTY_WRITE_ICON;
      }
    }
  }

  private boolean isStatic() {
    return myIsStatic;
  }

  @Override
  public String getPresentableText() {
    return myPropertyName + ": " + myTypeText;
  }

  public String toString() {
    return myPropertyName;
  }


  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PropertyGroup)) return false;

    return myPropertyName.equals(((PropertyGroup)o).myPropertyName) && myTypeText.equals(((PropertyGroup)o).myTypeText);
  }

  public int hashCode() {
    return myPropertyName.hashCode() * 31 + myTypeText.hashCode();
  }

  @Override
  public int getAccessLevel() {
    int result = PsiUtil.ACCESS_LEVEL_PRIVATE;
    if (getGetter() != null) {
      result = Math.max(result, PsiUtil.getAccessLevel(getGetter().getModifierList()));
    }
    if (getSetter() != null) {
      result = Math.max(result, PsiUtil.getAccessLevel(getSetter().getModifierList()));
    }
    if (getField() != null) {
      result = Math.max(result, PsiUtil.getAccessLevel(getField().getModifierList()));
    }
    return result;
  }

  @Override
  public int getSubLevel() {
    return 0;
  }

  public void setField(PsiField field) {
    myFieldPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(field);
    myIsStatic &= field.hasModifierProperty(PsiModifier.STATIC);
  }

  public void setGetter(PsiMethod getter) {
    myGetterPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(getter);
    myIsStatic &= getter.hasModifierProperty(PsiModifier.STATIC);
  }

  public void setSetter(PsiMethod setter) {
    mySetterPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(setter);
    myIsStatic &= setter.hasModifierProperty(PsiModifier.STATIC);
  }

  public PsiField getField() {
    return (PsiField)(myFieldPointer == null ? null : myFieldPointer.getElement());
  }

  public PsiMethod getGetter() {
    return (PsiMethod)(myGetterPointer == null ? null : myGetterPointer.getElement());
  }

  public PsiMethod getSetter() {
    return (PsiMethod)(mySetterPointer == null ? null : mySetterPointer.getElement());
  }

  void copyAccessorsFrom(PropertyGroup group) {
    if (group.getGetter() != null) setGetter(group.getGetter());
    if (group.getSetter() != null) setSetter(group.getSetter());
    if (group.getField() != null) setField(group.getField());
    myChildren.addAll(group.myChildren);
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    return isDeprecated() ? CodeInsightColors.DEPRECATED_ATTRIBUTES : null;
  }

  private boolean isDeprecated() {
    return isDeprecated(getField()) && isDeprecated(getGetter()) && isDeprecated(getSetter());
  }

  private static boolean isDeprecated(@Nullable final PsiDocCommentOwner element) {
    try {
      return element != null && element.isValid() && element.isDeprecated();
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  public boolean isComplete() {
    return getGetter() != null || getSetter() != null;
  }

  public Object getValue() {
    return this;
  }

  @Override
  public int getWeight() {
    return 60;
  }
}

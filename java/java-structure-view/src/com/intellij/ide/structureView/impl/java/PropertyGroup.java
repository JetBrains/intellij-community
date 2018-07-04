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

import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public class PropertyGroup implements Group, ColoredItemPresentation, AccessLevelProvider, WeighedItem {
  @NotNull private final String myPropertyName;
  @NotNull private final String myTypeText;

  private SmartPsiElementPointer myFieldPointer;
  private SmartPsiElementPointer myGetterPointer;
  private SmartPsiElementPointer mySetterPointer;
  private boolean myIsStatic;
  public static final Icon PROPERTY_READ_ICON = loadIcon("/nodes/propertyRead.png");
  public static final Icon PROPERTY_READ_STATIC_ICON = loadIcon("/nodes/propertyReadStatic.png");
  public static final Icon PROPERTY_WRITE_ICON = loadIcon("/nodes/propertyWrite.png");
  public static final Icon PROPERTY_WRITE_STATIC_ICON = loadIcon("/nodes/propertyWriteStatic.png");
  public static final Icon PROPERTY_READ_WRITE_ICON = loadIcon("/nodes/propertyReadWrite.png");
  public static final Icon PROPERTY_READ_WRITE_STATIC_ICON = loadIcon("/nodes/propertyReadWriteStatic.png");
  private final Project myProject;
  private final Collection<TreeElement> myChildren = new ArrayList<>();

  private PropertyGroup(@NotNull String propertyName, @NotNull PsiType propertyType, boolean isStatic, @NotNull Project project) {
    myPropertyName = propertyName;
    myTypeText = propertyType.getPresentableText();
    myIsStatic = isStatic;
    myProject = project;
  }

  public static PropertyGroup createOn(PsiElement object, final TreeElement treeElement) {
    if (object instanceof PsiField) {
      PsiField field = (PsiField)object;
      PropertyGroup group = new PropertyGroup(PropertyUtilBase.suggestPropertyName(field), field.getType(),
                                              field.hasModifierProperty(PsiModifier.STATIC), object.getProject());
      group.setField(field);
      group.myChildren.add(treeElement);
      return group;
    }
    else if (object instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)object;
      if (PropertyUtilBase.isSimplePropertyGetter(method)) {
        PropertyGroup group = new PropertyGroup(PropertyUtilBase.getPropertyNameByGetter(method), method.getReturnType(),
                                                method.hasModifierProperty(PsiModifier.STATIC), object.getProject());
        group.setGetter(method);
        group.myChildren.add(treeElement);
        return group;
      }
      else if (PropertyUtilBase.isSimplePropertySetter(method)) {
        PropertyGroup group =
          new PropertyGroup(PropertyUtilBase.getPropertyNameBySetter(method), method.getParameterList().getParameters()[0].getType(),
                            method.hasModifierProperty(PsiModifier.STATIC), object.getProject());
        group.setSetter(method);
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
  public String getLocationString() {
    return null;
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

  private static Icon loadIcon(@NonNls String resourceName) {
    Icon icon = IconLoader.findIcon(resourceName);
    Application application = ApplicationManager.getApplication();
    if (icon == null && application != null && application.isUnitTestMode()) {
      return new ImageIcon();
    }
    return icon;
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

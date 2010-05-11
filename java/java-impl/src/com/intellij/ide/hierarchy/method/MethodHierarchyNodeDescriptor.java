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
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;

import javax.swing.*;
import java.awt.*;

public final class MethodHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  private static final Icon METHOD_DEFINED_ICON = IconLoader.getIcon("/hierarchy/methodDefined.png");
  private static final Icon METHOD_NOT_DEFINED_ICON = IconLoader.getIcon("/hierarchy/methodNotDefined.png");
  private static final Icon SHOULD_DEFINE_METHOD_ICON = IconLoader.getIcon("/hierarchy/shouldDefineMethod.png");

  private Icon myRawIcon;
  private Icon myStateIcon;
  private MethodHierarchyTreeStructure myTreeStructure;

  public MethodHierarchyNodeDescriptor(
    final Project project,
    final HierarchyNodeDescriptor parentDescriptor,
    final PsiClass aClass,
    final boolean isBase,
    final MethodHierarchyTreeStructure treeStructure
  ){
    super(project, parentDescriptor, aClass, isBase);
    myTreeStructure = treeStructure;
  }

  public final void setTreeStructure(final MethodHierarchyTreeStructure treeStructure) {
    myTreeStructure = treeStructure;
  }

  private PsiMethod getMethod(final PsiClass aClass, final boolean checkBases) {
    return MethodHierarchyUtil.findBaseMethodInClass(myTreeStructure.getBaseMethod(), aClass, checkBases);
  }

  public final PsiClass getPsiClass() {
    return (PsiClass)myElement;
  }

  /**
   * Element for OpenFileDescriptor
   */
  public final PsiElement getTargetElement() {
    final PsiClass aClass = getPsiClass();
    if (aClass == null || !aClass.isValid()) return null;
    final PsiMethod method = getMethod(aClass, false);
    if (method != null) return method;
    return aClass;
  }

  public final boolean isValid() {
    final PsiClass aClass = getPsiClass();
    return aClass != null && aClass.isValid();
  }

  public final boolean update() {
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()){
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    boolean changes = super.update();

    final PsiClass psiClass = getPsiClass();

    if (psiClass == null){
      final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
      if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
        myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
      }
      return true;
    }

    final Icon newRawIcon = psiClass.getIcon(flags);
    final Icon newStateIcon = calculateState(psiClass);

    if (changes || newRawIcon != myRawIcon || newStateIcon != myStateIcon) {
      changes = true;

      myRawIcon = newRawIcon;
      myStateIcon = newStateIcon;

      myOpenIcon = myRawIcon;

      if (myIsBase) {
        final LayeredIcon icon = new LayeredIcon(2);
        icon.setIcon(myOpenIcon, 0);
        icon.setIcon(BASE_POINTER_ICON, 1, -BASE_POINTER_ICON.getIconWidth() / 2, 0);
        myOpenIcon = icon;
      }

      if (myStateIcon != null) {
        final RowIcon icon = new RowIcon(2);
        icon.setIcon(myStateIcon, 0);
        icon.setIcon(myOpenIcon, 1);
        myOpenIcon = icon;
      }

      myClosedIcon = myOpenIcon;
    }

    final CompositeAppearance oldText = myHighlightedText;

    myHighlightedText = new CompositeAppearance();
    TextAttributes classNameAttributes = null;
    if (myColor != null) {
      classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass(psiClass, false), classNameAttributes);
    myHighlightedText.getEnding().addText("  (" + JavaHierarchyUtil.getPackageName(psiClass) + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }

  private Icon calculateState(final PsiClass psiClass) {
    final PsiMethod method = getMethod(psiClass, false);
    if (method != null) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return null;
      }
      return METHOD_DEFINED_ICON;
    }

    if (myTreeStructure.isSuperClassForBaseClass(psiClass)) {
      return METHOD_NOT_DEFINED_ICON;
    }

    final boolean isAbstractClass = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    // was it implemented is in superclasses?
    final PsiMethod baseClassMethod = getMethod(psiClass, true);

    final boolean hasBaseImplementation = baseClassMethod != null && !baseClassMethod.hasModifierProperty(PsiModifier.ABSTRACT);

    if (hasBaseImplementation || isAbstractClass) {
      return METHOD_NOT_DEFINED_ICON;
    }
    else {
      return SHOULD_DEFINE_METHOD_ICON;
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.icons.AllIcons;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class MethodHierarchyNodeDescriptor extends HierarchyNodeDescriptor {

  private Icon myRawIcon;
  private Icon myStateIcon;
  private MethodHierarchyTreeStructure myTreeStructure;

  MethodHierarchyNodeDescriptor(@NotNull Project project,
                                HierarchyNodeDescriptor parentDescriptor,
                                @NotNull PsiElement aClass,
                                boolean isBase,
                                @NotNull MethodHierarchyTreeStructure treeStructure) {
    super(project, parentDescriptor, aClass, isBase);
    myTreeStructure = treeStructure;
  }

  public void setTreeStructure(MethodHierarchyTreeStructure treeStructure) {
    myTreeStructure = treeStructure;
  }

  @Nullable
  PsiMethod getMethod(PsiClass aClass, boolean checkBases) {
    try {
      return MethodHierarchyUtil.findBaseMethodInClass(myTreeStructure.getBaseMethod(), aClass, checkBases);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  public PsiElement getPsiClass() {
    return getPsiElement();
  }

  /**
   * Element for OpenFileDescriptor
   */
  public PsiElement getTargetElement() {
    PsiElement element = getPsiClass();
    if (!(element instanceof PsiClass)) return element;
    PsiClass aClass = (PsiClass)getPsiClass();
    if (!aClass.isValid()) return null;
    PsiMethod method = getMethod(aClass, false);
    if (method != null) return method;
    return aClass;
  }

  @Override
  public boolean update() {
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()){
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    boolean changes = super.update();

    PsiElement psiClass = getPsiClass();

    if (psiClass == null){
      return invalidElement();
    }

    Icon newRawIcon = psiClass.getIcon(flags);
    Icon newStateIcon = psiClass instanceof PsiClass ? calculateState((PsiClass)psiClass) : AllIcons.Hierarchy.MethodDefined;

    if (changes || newRawIcon != myRawIcon || newStateIcon != myStateIcon) {
      changes = true;

      myRawIcon = newRawIcon;
      myStateIcon = newStateIcon;

      Icon newIcon = myRawIcon;

      if (myIsBase) {
        newIcon = getBaseMarkerIcon(newIcon);
      }

      if (myStateIcon != null) {
        newIcon = IconManager.getInstance().createRowIcon(myStateIcon, newIcon);
      }

      setIcon(newIcon);
    }

    CompositeAppearance oldText = myHighlightedText;

    myHighlightedText = new CompositeAppearance();
    TextAttributes classNameAttributes = null;
    if (myColor != null) {
      classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (psiClass instanceof PsiClass) {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)psiClass, false), classNameAttributes);
      myHighlightedText.getEnding().addText("  (" + JavaHierarchyUtil.getPackageName((PsiClass)psiClass) + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    } else if (psiClass instanceof PsiFunctionalExpression) {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getFunctionalExpressionPresentation((PsiFunctionalExpression)psiClass, false));
    }
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }

  private Icon calculateState(PsiClass psiClass) {
    PsiMethod method = getMethod(psiClass, false);
    if (method != null) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return null;
      }
      return AllIcons.Hierarchy.MethodDefined;
    }

    if (myTreeStructure.isSuperClassForBaseClass(psiClass)) {
      return AllIcons.Hierarchy.MethodNotDefined;
    }

    boolean isAbstractClass = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    // was it implemented is in superclasses?
    PsiMethod baseClassMethod = getMethod(psiClass, true);

    boolean hasBaseImplementation = baseClassMethod != null && !baseClassMethod.hasModifierProperty(PsiModifier.ABSTRACT);

    if (hasBaseImplementation || isAbstractClass) {
      return AllIcons.Hierarchy.MethodNotDefined;
    }
    else {
      return AllIcons.Hierarchy.ShouldDefineMethod;
    }
  }
}

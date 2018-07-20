/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class MethodHierarchyNodeDescriptor extends HierarchyNodeDescriptor {

  private Icon myRawIcon;
  private Icon myStateIcon;
  private MethodHierarchyTreeStructure myTreeStructure;

  public MethodHierarchyNodeDescriptor(final Project project,
                                       final HierarchyNodeDescriptor parentDescriptor,
                                       final PsiElement aClass,
                                       final boolean isBase,
                                       final MethodHierarchyTreeStructure treeStructure
  ) {
    super(project, parentDescriptor, aClass, isBase);
    myTreeStructure = treeStructure;
  }

  public final void setTreeStructure(final MethodHierarchyTreeStructure treeStructure) {
    myTreeStructure = treeStructure;
  }

  @Nullable
  PsiMethod getMethod(final PsiClass aClass, final boolean checkBases) {
    try {
      return MethodHierarchyUtil.findBaseMethodInClass(myTreeStructure.getBaseMethod(), aClass, checkBases);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  public final PsiElement getPsiClass() {
    return getPsiElement();
  }

  /**
   * Element for OpenFileDescriptor
   */
  public final PsiElement getTargetElement() {
    final PsiElement element = getPsiClass();
    if (!(element instanceof PsiClass)) return element;
    final PsiClass aClass = (PsiClass)getPsiClass();
    if (!aClass.isValid()) return null;
    final PsiMethod method = getMethod(aClass, false);
    if (method != null) return method;
    return aClass;
  }

  public final boolean update() {
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()){
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    boolean changes = super.update();

    final PsiElement psiClass = getPsiClass();

    if (psiClass == null){
      return invalidElement();
    }

    final Icon newRawIcon = psiClass.getIcon(flags);
    final Icon newStateIcon = psiClass instanceof PsiClass ? calculateState((PsiClass)psiClass) : AllIcons.Hierarchy.MethodDefined;

    if (changes || newRawIcon != myRawIcon || newStateIcon != myStateIcon) {
      changes = true;

      myRawIcon = newRawIcon;
      myStateIcon = newStateIcon;

      Icon newIcon = myRawIcon;

      if (myIsBase) {
        final LayeredIcon icon = new LayeredIcon(2);
        icon.setIcon(newIcon, 0);
        icon.setIcon(AllIcons.Actions.Forward, 1, -AllIcons.Actions.Forward.getIconWidth() / 2, 0);
        newIcon = icon;
      }

      if (myStateIcon != null) {
        newIcon = new RowIcon(myStateIcon, newIcon);
      }

      setIcon(newIcon);
    }

    final CompositeAppearance oldText = myHighlightedText;

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

  private Icon calculateState(final PsiClass psiClass) {
    final PsiMethod method = getMethod(psiClass, false);
    if (method != null) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return null;
      }
      return AllIcons.Hierarchy.MethodDefined;
    }

    if (myTreeStructure.isSuperClassForBaseClass(psiClass)) {
      return AllIcons.Hierarchy.MethodNotDefined;
    }

    final boolean isAbstractClass = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    // was it implemented is in superclasses?
    final PsiMethod baseClassMethod = getMethod(psiClass, true);

    final boolean hasBaseImplementation = baseClassMethod != null && !baseClassMethod.hasModifierProperty(PsiModifier.ABSTRACT);

    if (hasBaseImplementation || isAbstractClass) {
      return AllIcons.Hierarchy.MethodNotDefined;
    }
    else {
      return AllIcons.Hierarchy.ShouldDefineMethod;
    }
  }
}

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
package com.intellij.ide.hierarchy.type;

import com.intellij.icons.AllIcons;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.LayeredIcon;

import java.awt.*;

public final class TypeHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  public TypeHierarchyNodeDescriptor(final Project project, final HierarchyNodeDescriptor parentDescriptor, final PsiElement classOrFunctionalExpression, final boolean isBase) {
    super(project, parentDescriptor, classOrFunctionalExpression, isBase);
  }

  public final PsiElement getPsiClass() {
    return getPsiElement();
  }

  public final boolean update() {
    boolean changes = super.update();

    if (getPsiElement() == null) {
      return invalidElement();
    }

    if (changes && myIsBase) {
      final LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(getIcon(), 0);
      icon.setIcon(AllIcons.Actions.Forward, 1, -AllIcons.Actions.Forward.getIconWidth() / 2, 0);
      setIcon(icon);
    }

    final PsiElement psiElement = getPsiClass();

    final CompositeAppearance oldText = myHighlightedText;

    myHighlightedText = new CompositeAppearance();

    TextAttributes classNameAttributes = null;
    if (myColor != null) {
      classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (psiElement instanceof PsiClass) {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)psiElement, false), classNameAttributes);
      myHighlightedText.getEnding().addText(" (" + JavaHierarchyUtil.getPackageName((PsiClass)psiElement) + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    } else if (psiElement instanceof PsiFunctionalExpression) {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getFunctionalExpressionPresentation(((PsiFunctionalExpression)psiElement), false));
    }
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }

}

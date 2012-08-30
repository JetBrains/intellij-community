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
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.LayeredIcon;

import java.awt.*;

public final class TypeHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  public TypeHierarchyNodeDescriptor(final Project project, final HierarchyNodeDescriptor parentDescriptor, final PsiClass psiClass, final boolean isBase) {
    super(project, parentDescriptor, psiClass, isBase);
  }

  public final PsiClass getPsiClass() {
    return (PsiClass)myElement;
  }

  public final boolean isValid() {
    final PsiClass aClass = getPsiClass();
    return aClass != null && aClass.isValid();
  }

  public final boolean update() {
    boolean changes = super.update();

    if (myElement == null){
      final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
      if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
        myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
      }
      return true;
    }

    if (changes && myIsBase) {
      final LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(getIcon(), 0);
      icon.setIcon(AllIcons.Hierarchy.Base, 1, -AllIcons.Hierarchy.Base.getIconWidth() / 2, 0);
      setIcon(icon);
    }

    final PsiClass psiClass = getPsiClass();

    final CompositeAppearance oldText = myHighlightedText;

    myHighlightedText = new CompositeAppearance();

    TextAttributes classNameAttributes = null;
    if (myColor != null) {
      classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass(psiClass, false), classNameAttributes);
    myHighlightedText.getEnding().addText(" (" + JavaHierarchyUtil.getPackageName(psiClass) + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }

}

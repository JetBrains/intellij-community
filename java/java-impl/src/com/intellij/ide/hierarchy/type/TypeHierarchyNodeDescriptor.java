package com.intellij.ide.hierarchy.type;

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
      icon.setIcon(myOpenIcon, 0);
      icon.setIcon(BASE_POINTER_ICON, 1, -BASE_POINTER_ICON.getIconWidth() / 2, 0);
      myOpenIcon = icon;
      myClosedIcon = myOpenIcon;
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

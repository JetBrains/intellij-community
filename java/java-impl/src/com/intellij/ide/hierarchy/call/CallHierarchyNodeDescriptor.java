/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.hierarchy.call;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.*;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class CallHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {
  private int myUsageCount = 1;
  private final List<PsiReference> myReferences = new ArrayList<>();
  private final boolean myNavigateToReference;

  public CallHierarchyNodeDescriptor(@NotNull Project project,
                                     final HierarchyNodeDescriptor parentDescriptor,
                                     @NotNull PsiElement element,
                                     final boolean isBase,
                                     final boolean navigateToReference) {
    super(project, parentDescriptor, element, isBase);
    myNavigateToReference = navigateToReference;
  }

  /**
   * @return PsiMethod or PsiClass or JspFile
   */
  public final PsiMember getEnclosingElement(){
    PsiElement element = getPsiElement();
    return element == null ? null : getEnclosingElement(element);
  }

  public static PsiMember getEnclosingElement(final PsiElement element){
    return PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class, PsiClass.class);
  }

  public final void incrementUsageCount(){
    myUsageCount++;
  }

  /**
   * Element for OpenFileDescriptor
   */
  public final PsiElement getTargetElement(){
    return getPsiElement();
  }

  public final boolean isValid(){
    return getEnclosingElement() != null;
  }

  public final boolean update(){
    final CompositeAppearance oldText = myHighlightedText;
    final Icon oldIcon = getIcon();

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    boolean changes = super.update();

    final PsiElement enclosingElement = getEnclosingElement();

    if (enclosingElement == null) {
      final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
      if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
        myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
      }
      return true;
    }

    Icon newIcon = enclosingElement.getIcon(flags);
    if (changes && myIsBase) {
      final LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(newIcon, 0);
      icon.setIcon(AllIcons.Hierarchy.Base, 1, -AllIcons.Hierarchy.Base.getIconWidth() / 2, 0);
      newIcon = icon;
    }
    setIcon(newIcon);

    myHighlightedText = new CompositeAppearance();
    TextAttributes mainTextAttributes = null;
    if (myColor != null) {
      mainTextAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (enclosingElement instanceof PsiMethod) {
      if (enclosingElement instanceof SyntheticElement) {
        PsiFile file = enclosingElement.getContainingFile();
        myHighlightedText.getEnding().addText(file != null ? file.getName() : IdeBundle.message("node.call.hierarchy.unknown.jsp"), mainTextAttributes);
      }
      else {
        final PsiMethod method = (PsiMethod)enclosingElement;
        final StringBuilder buffer = new StringBuilder(128);
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
          buffer.append('.');
        }
        final String methodText = PsiFormatUtil.formatMethod(
          method,
          PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
          PsiFormatUtilBase.SHOW_TYPE
        );
        buffer.append(methodText);

        myHighlightedText.getEnding().addText(buffer.toString(), mainTextAttributes);
      }
    }
    else if (FileTypeUtils.isInServerPageFile(enclosingElement) && enclosingElement instanceof PsiFile) {
      final PsiFile file = PsiUtilCore.getTemplateLanguageFile(enclosingElement);
      myHighlightedText.getEnding().addText(file.getName(), mainTextAttributes);
    }
    else {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)enclosingElement, false), mainTextAttributes);
    }
    if (myUsageCount > 1) {
      myHighlightedText.getEnding().addText(IdeBundle.message("node.call.hierarchy.N.usages", myUsageCount), HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
    }
    if (!(FileTypeUtils.isInServerPageFile(enclosingElement) && enclosingElement instanceof PsiFile)) {
      final PsiClass containingClass = enclosingElement instanceof PsiMethod
                                       ? ((PsiMethod)enclosingElement).getContainingClass()
                                       : (PsiClass)enclosingElement;
      if (containingClass != null) {
        final String packageName = JavaHierarchyUtil.getPackageName(containingClass);
        myHighlightedText.getEnding().addText("  (" + packageName + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
      }
    }
    myName = myHighlightedText.getText();

    if (
      !Comparing.equal(myHighlightedText, oldText) ||
      !Comparing.equal(getIcon(), oldIcon)
    ){
      changes = true;
    }
    return changes;
  }

  public void addReference(final PsiReference reference) {
    myReferences.add(reference);
  }

  public boolean hasReference(PsiReference reference) {
    return myReferences.contains(reference);
  }

  public void navigate(boolean requestFocus) {
    if (!myNavigateToReference) {
      PsiElement element = getPsiElement();
      if (element instanceof Navigatable && ((Navigatable)element).canNavigate()) {
        ((Navigatable)element).navigate(requestFocus);
      }
      return;
    }

    final PsiReference firstReference = myReferences.get(0);
    final PsiElement element = firstReference.getElement();
    if (element == null) return;
    final PsiElement callElement = element.getParent();
    if (callElement instanceof Navigatable && ((Navigatable)callElement).canNavigate()) {
      ((Navigatable)callElement).navigate(requestFocus);
    } else {
      final PsiFile psiFile = callElement.getContainingFile();
      if (psiFile == null || psiFile.getVirtualFile() == null) return;
      FileEditorManager.getInstance(myProject).openFile(psiFile.getVirtualFile(), requestFocus);
    }

    Editor editor = PsiUtilBase.findEditor(callElement);

    if (editor != null) {

      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
      for (PsiReference psiReference : myReferences) {
        final PsiElement eachElement = psiReference.getElement();
        if (eachElement != null) {
          final PsiElement eachMethodCall = eachElement.getParent();
          if (eachMethodCall != null) {
            final TextRange textRange = eachMethodCall.getTextRange();
            highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, false, highlighters);
          }
        }
      }
    }
  }

  public boolean canNavigate() {
    if (!myNavigateToReference) {
      PsiElement element = getPsiElement();
      return element instanceof Navigatable && ((Navigatable)element).canNavigate();
    }
    if (myReferences.isEmpty()) return false;
    final PsiReference firstReference = myReferences.get(0);
    final PsiElement callElement = firstReference.getElement().getParent();
    if (callElement == null || !callElement.isValid()) return false;
    if (!(callElement instanceof Navigatable) || !((Navigatable)callElement).canNavigate()) {
      final PsiFile psiFile = callElement.getContainingFile();
      if (psiFile == null) return false;
    }
    return true;
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }
}

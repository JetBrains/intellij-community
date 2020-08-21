// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.*;
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
  public final PsiMember getEnclosingElement() {
    PsiElement element = getPsiElement();
    return element == null ? null : getEnclosingElement(element);
  }

  public static PsiMember getEnclosingElement(final PsiElement element) {
    return PsiTreeUtil.getNonStrictParentOfType(element, PsiField.class, PsiMethod.class, PsiClass.class);
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

  @Override
  public final boolean isValid(){
    return getEnclosingElement() != null;
  }

  @Override
  public final boolean update() {
    final CompositeAppearance oldText = myHighlightedText;
    final Icon oldIcon = getIcon();

    boolean changes = super.update();

    PsiMember enclosingElement = getEnclosingElement();

    if (enclosingElement == null) {
      return invalidElement();
    }

    installIcon(enclosingElement, changes);

    myHighlightedText = new CompositeAppearance();
    TextAttributes mainTextAttributes = null;
    if (myColor != null) {
      mainTextAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (enclosingElement instanceof PsiMethod || enclosingElement instanceof PsiField) {
      if (enclosingElement instanceof SyntheticElement) {
        PsiFile file = enclosingElement.getContainingFile();
        myHighlightedText.getEnding().addText(file != null ? file.getName() : JavaBundle.message("node.call.hierarchy.unknown.jsp"), mainTextAttributes);
      }
      else {
        @NlsSafe StringBuilder buffer = new StringBuilder(128);
        PsiClass containingClass = enclosingElement.getContainingClass();
        if (containingClass != null) {
          buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
          buffer.append('.');
        }
        String methodText =
          enclosingElement instanceof PsiMethod
          ? PsiFormatUtil.formatMethod(
            (PsiMethod)enclosingElement,
            PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
            PsiFormatUtilBase.SHOW_TYPE)
          : enclosingElement.getName();
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
      PsiClass containingClass = enclosingElement.getContainingClass();
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

  @Override
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
    final PsiElement callElement = element.getParent();
    if (callElement instanceof Navigatable && ((Navigatable)callElement).canNavigate()) {
      ((Navigatable)callElement).navigate(requestFocus);
    }
    else {
      final PsiFile psiFile = callElement.getContainingFile();
      if (psiFile == null || psiFile.getVirtualFile() == null) return;
      FileEditorManager.getInstance(myProject).openFile(psiFile.getVirtualFile(), requestFocus);
    }

    Editor editor = PsiEditorUtil.findEditor(callElement);

    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
      for (PsiReference psiReference : myReferences) {
        final PsiElement eachElement = psiReference.getElement();
        final PsiElement eachMethodCall = eachElement.getParent();
        if (eachMethodCall != null) {
          final TextRange textRange = eachMethodCall.getTextRange();
          highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), 
                                             EditorColors.SEARCH_RESULT_ATTRIBUTES, false, highlighters);
        }
      }
    }
  }

  @Override
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

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }
}

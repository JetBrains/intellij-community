// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
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
                                     HierarchyNodeDescriptor parentDescriptor,
                                     @NotNull PsiElement element,
                                     boolean isBase,
                                     boolean navigateToReference) {
    super(project, parentDescriptor, element, isBase);
    myNavigateToReference = navigateToReference;
  }

  /**
   * @return PsiMethod or PsiClass or JspFile
   */
  public PsiMember getEnclosingElement() {
    PsiElement element = getPsiElement();
    if (element instanceof PsiClass aClass && aClass.isRecord()) {
      return JavaPsiRecordUtil.findCanonicalConstructor(aClass);
    }
    return element == null ? null : getEnclosingElement(element);
  }

  public static PsiMember getEnclosingElement(PsiElement element) {
    return PsiTreeUtil.getNonStrictParentOfType(element, PsiRecordComponent.class, PsiField.class, PsiMethod.class, PsiClass.class);
  }

  public void incrementUsageCount(){
    myUsageCount++;
  }

  /**
   * Element for OpenFileDescriptor
   */
  public PsiElement getTargetElement(){
    return getPsiElement();
  }

  @Override
  public boolean isValid(){
    return getEnclosingElement() != null;
  }

  @Override
  public boolean update() {
    CompositeAppearance oldText = myHighlightedText;
    Icon oldIcon = getIcon();

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
    if (enclosingElement instanceof PsiMethod || enclosingElement instanceof PsiField || enclosingElement instanceof PsiRecordComponent) {
      if (FileTypeUtils.isInServerPageFile(enclosingElement)) {
        PsiFile file = enclosingElement.getContainingFile();
        String text = file != null ? file.getName() : JavaBundle.message("node.call.hierarchy.unknown.jsp");
        myHighlightedText.getEnding().addText(text, mainTextAttributes);
      }
      else {
        PsiClass containingClass = enclosingElement.getContainingClass();
        String className = containingClass == null ? null : ClassPresentationUtil.getNameForClass(containingClass, false);
        String methodName =
          enclosingElement instanceof PsiMethod method
          ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                       PsiFormatUtilBase.SHOW_TYPE)
          : enclosingElement.getName();

        String fullMethodName = className == null ? methodName :
                                containingClass.getContainingClass() == null ?
                                className +"."+methodName :
                                // in case of complex nested classes prefer "foo() in Inner in Outer" instead of confusing "Inner in Outer.foo()"
                                JavaPsiBundle.message("class.context.display", methodName, className);

        myHighlightedText.getEnding().addText(fullMethodName, mainTextAttributes);
      }
    }
    else if (FileTypeUtils.isInServerPageFile(enclosingElement) && enclosingElement instanceof PsiFile) {
      PsiFile file = PsiUtilCore.getTemplateLanguageFile(enclosingElement);
      myHighlightedText.getEnding().addText(file.getName(), mainTextAttributes);
    }
    else {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)enclosingElement, false), mainTextAttributes);
    }
    if (myUsageCount > 1) {
      myHighlightedText.getEnding().addText(IdeBundle.message("node.call.hierarchy.N.usages", myUsageCount), getUsageCountPrefixAttributes());
    }
    if (!(FileTypeUtils.isInServerPageFile(enclosingElement) && enclosingElement instanceof PsiFile)) {
      PsiClass containingClass = enclosingElement.getContainingClass();
      if (containingClass != null) {
        String packageName = JavaHierarchyUtil.getPackageName(containingClass);
        myHighlightedText.getEnding().addText("  (" + packageName + ")", getPackageNameAttributes());
      }
    }
    myName = myHighlightedText.getText();

    changes |= !Comparing.equal(myHighlightedText, oldText) || !Comparing.equal(getIcon(), oldIcon);
    return changes;
  }

  public void addReference(PsiReference reference) {
    myReferences.add(reference);
  }

  public boolean hasReference(PsiReference reference) {
    return myReferences.contains(reference);
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (!myNavigateToReference) {
      PsiElement element = getPsiElement();
      if (element instanceof Navigatable navigatable && navigatable.canNavigate()) {
        navigatable.navigate(requestFocus);
      }
      return;
    }

    PsiReference firstReference = myReferences.get(0);
    PsiElement element = firstReference.getElement();
    PsiElement callElement = element.getParent();
    if (callElement instanceof Navigatable navigatable && navigatable.canNavigate()) {
      navigatable.navigate(requestFocus);
    }
    else {
      PsiFile psiFile = callElement.getContainingFile();
      if (psiFile == null || psiFile.getVirtualFile() == null) return;
      FileEditorManager.getInstance(myProject).openFile(psiFile.getVirtualFile(), requestFocus);
    }

    Editor editor = PsiEditorUtil.findEditor(callElement);

    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      List<RangeHighlighter> highlighters = new ArrayList<>();
      for (PsiReference psiReference : myReferences) {
        PsiElement eachElement = psiReference.getElement();
        PsiElement eachMethodCall = eachElement.getParent();
        if (eachMethodCall != null) {
          TextRange textRange = eachMethodCall.getTextRange();
          highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), 
                                             EditorColors.SEARCH_RESULT_ATTRIBUTES, false, highlighters);
        }
      }
    }
  }

  @Override
  public boolean canNavigate() {
    if (!myNavigateToReference) {
      return getPsiElement() instanceof Navigatable navigatable && navigatable.canNavigate();
    }
    if (myReferences.isEmpty()) return false;
    PsiReference firstReference = myReferences.get(0);
    PsiElement callElement = firstReference.getElement().getParent();
    if (callElement == null || !callElement.isValid()) return false;
    if (!(callElement instanceof Navigatable navigatable) || !navigatable.canNavigate()) {
      return callElement.getContainingFile() != null;
    }
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.ReferenceAwareNodeDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;

public final class CallHierarchyNodeDescriptor extends JavaHierarchyNodeDescriptor implements Navigatable, ReferenceAwareNodeDescriptor {
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
  @Override
  public @Nullable PsiMember getEnclosingElement() {
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

    myHighlightedText = getEnclosingElementAppearance(enclosingElement, true);
    if (myUsageCount > 1) {
      String usagesText = IdeBundle.message("node.call.hierarchy.N.usages", myUsageCount);
      myHighlightedText.getEnding().addText(usagesText, getUsageCountPrefixAttributes());
    }
    if (!(FileTypeUtils.isInServerPageFile(enclosingElement) && enclosingElement instanceof PsiFile)) {
      appendLocationPath(myHighlightedText, enclosingElement);
    }
    myName = myHighlightedText.getText();

    changes |= !Comparing.equal(myHighlightedText, oldText) || !Comparing.equal(getIcon(), oldIcon);
    return changes;
  }

  private @NotNull CompositeAppearance getEnclosingElementAppearance(@NotNull PsiMember enclosingElement, boolean withAttributes) {
    CompositeAppearance appearance = new CompositeAppearance();
    TextAttributes mainTextAttributes = withAttributes ? baseColorAttributes() : null;
    if (enclosingElement instanceof PsiMethod || enclosingElement instanceof PsiField || enclosingElement instanceof PsiRecordComponent) {
      if (FileTypeUtils.isInServerPageFile(enclosingElement)) {
        PsiFile file = enclosingElement.getContainingFile();
        String text = file != null ? file.getName() : JavaBundle.message("node.call.hierarchy.unknown.jsp");
        appearance.getEnding().addText(text, mainTextAttributes);
      }
      else {
        String name =
          enclosingElement instanceof PsiMethod method
          ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                       PsiFormatUtilBase.SHOW_TYPE)
          : enclosingElement.getName();
        appearance.getEnding().addText(name, withAttributes ? textAttributesFor(enclosingElement) : null);
      }
    }
    else if (FileTypeUtils.isInServerPageFile(enclosingElement) && enclosingElement instanceof PsiFile) {
      PsiFile file = PsiUtilCore.getTemplateLanguageFile(enclosingElement);
      appearance.getEnding().addText(file.getName(), mainTextAttributes);
    }
    else {
      String simpleName = ClassPresentationUtil.getSimpleNameForClass((PsiClass)enclosingElement);
      appearance.getEnding().addText(simpleName, withAttributes ? textAttributesFor(enclosingElement) : null);
    }
    return appearance;
  }

  @Override
  public @Nullable @NlsSafe String getPresentation() {
    PsiMember enclosingElement = getEnclosingElement();
    if (enclosingElement == null) return null;
    return getEnclosingElementAppearance(enclosingElement, false).getText();
  }

  public void addReference(PsiReference reference) {
    myReferences.add(reference);
  }

  public boolean hasReference(PsiReference reference) {
    return myReferences.contains(reference);
  }

  /**
   * @return all the references that are associated with the current element during the "Call Hierarchy" request.
   */
  @Override
  public @NotNull List<PsiReference> getReferences() {
    return myReferences;
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

    PsiReference firstReference = myReferences.getFirst();
    PsiElement element = firstReference.getElement();
    PsiElement callElement = (element instanceof PsiNameIdentifierOwner) ? element : element.getParent();
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
      for (PsiReference ref : myReferences) {
        PsiElement eachElement = ref.getElement();
        PsiElement eachMethodCall = 
          eachElement instanceof PsiNameIdentifierOwner owner ? owner.getNameIdentifier() : eachElement.getParent();
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
    PsiReference firstReference = myReferences.getFirst();
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

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility methods for finding documentation target elements at a given editor offset.
 */
@Internal
public final class DocumentationTargetFinder {

  public static final Key<SmartPsiElementPointer<?>> ORIGINAL_ELEMENT_KEY = Key.create("Original element");
  public static final Key<Boolean> IS_FROM_LOOKUP = Key.create("IS FROM LOOKUP");

  private DocumentationTargetFinder() {
  }

  /**
   * @param offset current caret offset
   * @param file file in which the target element is searched
   * @param findTargetFromLookup whether the element could be found from current lookup
   * @return element for which to show documentation and (context) element at `file` given offset.
   */
  public static @Nullable DocumentationTargetFinder.TargetWithContext findTargetElementAndContext(
    @NotNull Project project,
    @NotNull Editor editor,
    int offset,
    @Nullable PsiFile file,
    boolean findTargetFromLookup
  ) {
    PsiElement originalElement = getContextElement(file, offset);
    PsiElement element = findTargetElementAtOffset(project, editor, offset, file, originalElement, findTargetFromLookup);
    if (element == null) {
      PsiElement list = ParameterInfoControllerBase.findArgumentList(file, offset, -1);
      if (list != null) {
        element = list;
      }
    }
    if (element == null && file == null) return null; //file == null for text field editor

    if (element == null) { // look if we are within a javadoc comment
      element = assertSameProject(project, originalElement);
      if (element == null) return null;

      PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class);
      if (comment == null) return null;

      element = comment instanceof PsiDocCommentBase ? ((PsiDocCommentBase)comment).getOwner() : comment.getParent();
      if (element == null) return null;
      //if (!(element instanceof PsiDocCommentOwner)) return null;
    }
    return new TargetWithContext(element, originalElement);
  }

  public static @Nullable PsiElement getContextElement(@NotNull Editor editor, @NotNull PsiFile file) {
    return getContextElement(file, editor.getCaretModel().getOffset());
  }

  static @Nullable PsiElement findTargetElementAtOffset(
    @NotNull Project project,
    @NotNull Editor editor,
    int offset,
    @Nullable PsiFile file,
    @Nullable PsiElement contextElement,
    boolean findTargetFromLookup
  ) {
    PsiElement element = assertSameProject(project, doFindTargetElementAtOffset(editor, offset, file, contextElement, findTargetFromLookup));
    storeOriginalElement(project, contextElement, element);
    storeIsFromLookup(element, false);
    return element;
  }

  static void storeOriginalElement(Project project, PsiElement originalElement, PsiElement element) {
    if (element == null) return;
    try {
      element.putUserData(
        ORIGINAL_ELEMENT_KEY,
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(originalElement)
      );
    }
    catch (RuntimeException ex) {
      // PsiPackage does not allow putUserData
    }
  }

  static void storeIsFromLookup(@Nullable PsiElement element, boolean value) {
    if (element == null) return;
    element.putUserData(IS_FROM_LOOKUP, value ? true : null);
  }


  static @NotNull DocumentationProvider getProviderFromElement(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
    if (element != null && !element.isValid()) {
      element = null;
    }
    if (originalElement != null && !originalElement.isValid()) {
      originalElement = null;
    }

    if (originalElement == null) {
      originalElement = getOriginalElement(element);
    }

    PsiFile containingFile =
      originalElement != null ? originalElement.getContainingFile() : element != null ? element.getContainingFile() : null;
    Set<DocumentationProvider> result = new LinkedHashSet<>();

    Language containingFileLanguage = containingFile != null ? containingFile.getLanguage() : null;
    DocumentationProvider originalProvider =
      containingFile != null ? LanguageDocumentation.INSTANCE.forLanguage(containingFileLanguage) : null;

    Language elementLanguage = element != null ? element.getLanguage() : null;
    DocumentationProvider elementProvider =
      element == null || elementLanguage.is(containingFileLanguage) ? null : LanguageDocumentation.INSTANCE.forLanguage(elementLanguage);

    ContainerUtil.addIfNotNull(result, elementProvider);
    ContainerUtil.addIfNotNull(result, originalProvider);

    if (containingFile != null) {
      Language baseLanguage = containingFile.getViewProvider().getBaseLanguage();
      if (!baseLanguage.is(containingFileLanguage)) {
        ContainerUtil.addIfNotNull(result, LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
      }
    }
    else if (element instanceof PsiDirectory) {
      Set<Language> set = new HashSet<>();

      for (PsiFile file : ((PsiDirectory)element).getFiles()) {
        Language baseLanguage = file.getViewProvider().getBaseLanguage();
        if (!set.contains(baseLanguage)) {
          set.add(baseLanguage);
          ContainerUtil.addIfNotNull(result, LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
        }
      }
    }
    return CompositeDocumentationProvider.wrapProviders(result);
  }

  static @Nullable PsiElement getOriginalElement(PsiElement element) {
    SmartPsiElementPointer<?> originalElementPointer = element != null ? element.getUserData(ORIGINAL_ELEMENT_KEY) : null;
    return originalElementPointer != null ? originalElementPointer.getElement() : null;
  }

  static @Nullable PsiElement assertSameProject(@NotNull Project project, @Nullable PsiElement element) {
    if (element != null && element.isValid() && project != element.getProject()) {
      throw new AssertionError(project + "!=" + element.getProject() + "; element=" + element);
    }
    return element;
  }

  private static @Nullable PsiElement getContextElement(@Nullable PsiFile file, int offset) {
    if (file == null) return null;
    if (offset == file.getTextLength()) {
      offset = Math.max(0, offset - 1);
    }
    return file.findElementAt(offset);
  }

  private static @Nullable PsiElement doFindTargetElementAtOffset(
    @NotNull Editor editor,
    int offset,
    @Nullable PsiFile file,
    @Nullable PsiElement contextElement,
    boolean findTargetFromLookup
  ) {
    PsiElement element;

    element = customElement(editor, file, offset, contextElement);
    if (element != null) {
      return element;
    }

    element = fromTargetUtil(editor, offset, contextElement, findTargetFromLookup);
    if (element != null) {
      return element;
    }

    return fromReference(editor, offset);
  }

  private static @Nullable PsiElement customElement(
    @NotNull Editor editor,
    @Nullable PsiFile file,
    int offset,
    @Nullable PsiElement contextElement
  ) {
    if (file == null) {
      return null;
    }
    return getProviderFromElement(file, null).getCustomDocumentationElement(editor, file, contextElement, offset);
  }

  private static @Nullable PsiElement fromTargetUtil(
    @NotNull Editor editor,
    int offset,
    @Nullable PsiElement contextElement,
    boolean findTargetFromLookup
  ) {
    TargetElementUtil util = TargetElementUtil.getInstance();

    int flags = util.getAllAccepted();
    if (!findTargetFromLookup) {
      flags &= (~TargetElementUtil.LOOKUP_ITEM_ACCEPTED);
    }
    PsiElement element = util.findTargetElement(editor, flags, offset);
    if (element == null && contextElement == null) {
      return null;
    }
    // Allow context doc over XML tag content
    PsiElement adjusted = util.adjustElement(editor, util.getAllAccepted(), element, contextElement);
    return adjusted != null ? adjusted : element;
  }

  private static @Nullable PsiElement fromReference(@NotNull Editor editor, int offset) {
    PsiReference ref = TargetElementUtil.findReference(editor, offset);
    if (ref == null) {
      return null;
    }
    if (ref instanceof PsiPolyVariantReference) {
      return ref.getElement();
    }
    return TargetElementUtil.getInstance().adjustReference(ref);
  }

  public record TargetWithContext(@NotNull PsiElement target, @Nullable PsiElement original) {}
}
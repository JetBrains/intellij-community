// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileWithOneLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import java.util.List;


final class TypedAutoPopupImpl {
  private static final Logger LOG = Logger.getInstance(TypedAutoPopupImpl.class);

  /**
   * Note: If you want to implement autopopup for an arbitrary character, consider adding your own {@link TypedHandlerDelegate}
   * and implement {@link TypedHandlerDelegate#checkAutoPopup}
   */
  static void autoPopupCompletion(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    if (charTyped == '.' ||
        (charTyped == '/' && Boolean.TRUE.equals(editor.getUserData(AutoPopupController.ALLOW_AUTO_POPUP_FOR_SLASHES_IN_PATHS))) ||// todo rewrite with TypedHandlerDelegate#checkAutoPopup
        isAutoPopup(editor, file, charTyped)
    ) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
    }
  }

  static void autoPopupParameterInfo(
    @NotNull Editor editor,
    char charTyped,
    @NotNull Project project,
    @NotNull PsiFile file
  ) {
    if ((charTyped == '(' || charTyped == ',') && !isInsideStringLiteral(editor, file)) {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }
  }

  /**
   * @return true if auto-popup should be invoked according to deprecated {@link CompletionContributor#invokeAutoPopup)}.
   */
  private static boolean isAutoPopup(
    @NotNull Editor editor,
    @NotNull PsiFile file,
    char charTyped
  ) {
    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) {
      return false;
    }
    PsiElement element;
    Language language;
    if (file instanceof PsiFileWithOneLanguage) {
      language = file.getLanguage();
      // we know the language, so let's try to avoid inferring the element at caret
      // because there might be no contributors, so inferring element would be a waste of time.
      element = null;
    } else {
      element = file.findElementAt(offset);
      if (element == null) {
        return false;
      }
      language = element.getLanguage();
    }
    List<CompletionContributor> contributors = CompletionContributor.forLanguageHonorDumbness(language, file.getProject());
    if (contributors.isEmpty()) {
      return false;
    }
    if (element == null) {
      // file is PsiFileWithOneLanguage, and there are contributors => we have to infer element.
      element = file.findElementAt(offset);
      if (element == null) {
        return false;
      }
    }
    PsiElement finalElement = element;
    CompletionContributor contributor = ContainerUtil.find(
      contributors,
      c -> c.invokeAutoPopup(finalElement, charTyped)
    );
    if (contributor == null) {
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(contributor + " requested completion autopopup when typing '" + charTyped + "'");
    }
    return true;
  }

  private static boolean isInsideStringLiteral(@NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return false;
    }
    Language language = element.getLanguage();
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (definition != null) {
      TokenSet stringLiteralElements = definition.getStringLiteralElements();
      ASTNode node = element.getNode();
      if (node == null) {
        return false;
      }
      IElementType elementType = node.getElementType();
      if (stringLiteralElements.contains(elementType)) {
        return true;
      }
      PsiElement parent = element.getParent();
      if (parent != null) {
        ASTNode parentNode = parent.getNode();
        return parentNode != null && stringLiteralElements.contains(parentNode.getElementType());
      }
    }
    return false;
  }
}

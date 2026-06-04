// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementCustomPreviewHolder;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactorJBundle;
import org.jetbrains.annotations.NotNull;

/// [LookupElement] to handle Markdown Javadoc template preview and insertion
final class JavadocMarkdownTemplateLookupElement extends LookupElement implements LookupElementCustomPreviewHolder {
  private final PsiJavaDocumentedElement targetElement;
  private String cachedTemplate = null;

  JavadocMarkdownTemplateLookupElement(@NotNull PsiJavaDocumentedElement element) { targetElement = element; }

  @Override
  public @NotNull String getLookupString() {
    return RefactorJBundle.message("insert.javadoc.template");
  }

  @Override
  public @NotNull IntentionPreviewInfo preview(@NotNull ActionContext ctx) {
    return new IntentionPreviewInfo.Snippet(JavaFileType.INSTANCE, "/// \n" + getTemplate(),
                                            targetElement.getContainingFile().getFileDocument().getLineNumber(ctx.offset()));
  }

  private String getTemplate() {
    if (cachedTemplate == null) {
      final CodeDocumentationProvider langDocumentationProvider = CodeDocumentationUtil.getCodeProvider(JavaLanguage.INSTANCE);
      assert langDocumentationProvider != null;

      String potentialDocs = langDocumentationProvider.generateDocumentationContentStub(targetElement.getDocComment());
      cachedTemplate = potentialDocs == null ? "" : potentialDocs.stripTrailing().stripIndent();
    }
    return cachedTemplate;
  }

  public boolean isAvailable() {
    return !StringUtil.isEmptyOrSpaces(getTemplate());
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    String docs = "\n" + getTemplate();
    context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), docs);
    context.commitDocument();

    PsiComment comment =
      PsiTreeUtil.getParentOfType(context.getFile().findElementAt(context.getTailOffset() - 1), PsiComment.class);
    if (comment == null) return;

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.getProject());
    CodeDocumentationUtil.formatComment(context.getFile(), comment, codeStyleManager);

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());

    context.getDocument().insertString(comment.getTextRange().getStartOffset() + 3, " ");
    context.getEditor().getCaretModel().moveToOffset(comment.getTextRange().getStartOffset() + 4);
  }
}

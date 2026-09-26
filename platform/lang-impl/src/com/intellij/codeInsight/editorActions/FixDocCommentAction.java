// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.documentation.DocCommentFixer;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.DocumentationStubProviderKt;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.DocCommentSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.CommentUtil.preferDocumentationLineComment;

/**
 * Creates documentation comment for the current context if it's not created yet (e.g. the caret is inside a method which
 * doesn't have a doc comment).
 * <p/>
 * Updates existing documentation comment if necessary if the one exists. E.g. we've changed method signature and want to remove all
 * outdated parameters and create stubs for the new ones.
 */
public final class FixDocCommentAction extends EditorAction {

  public static final @NotNull @NonNls String ACTION_ID = "FixDocComment";

  public FixDocCommentAction() {
    super(new MyHandler());
  }

  private static final class MyHandler extends EditorActionHandler {

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        return;
      }

      PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (psiFile == null) {
        return;
      }

      process(psiFile, editor, project, editor.getCaretModel().getOffset());
    }
  }

  private static void process(final @NotNull PsiFile file, final @NotNull Editor editor, final @NotNull Project project, int offset) {
    PsiElement elementAtOffset = file.findElementAt(offset);
    if (elementAtOffset == null || !FileModificationService.getInstance().preparePsiElementForWrite(elementAtOffset)) {
      return;
    }
    generateOrFixComment(elementAtOffset, project, editor);
  }

  /**
   * Generates a comment if it does not exist
   *
   * @param element     target element for which a comment should be generated
   * @param project     current project
   * @param navigator   navigator to use to set caret position
   */
  public static void generateComment(final @NotNull PsiElement element, final @NotNull Project project, final @NotNull ModNavigator navigator) {
    FixDocContext context = FixDocContext.find(element);
    if (context == null || context.existingComment() != null) {
      return;
    }
    Commenter c = LanguageCommenters.INSTANCE.forLanguage(context.language());
    if (!(c instanceof CodeDocumentationAwareCommenter commenter)) {
      return;
    }
    generateComment(context, navigator, commenter, project);
  }

  private static @Nullable CodeDocumentationProvider getDocumentationProvider(Language language) {
    final DocumentationProvider langDocumentationProvider = LanguageDocumentation.INSTANCE.forLanguage(language);
    if (langDocumentationProvider instanceof CompositeDocumentationProvider provider) {
      return provider.getFirstCodeDocumentationProvider();
    }
    if (langDocumentationProvider instanceof CodeDocumentationProvider provider) {
      return provider;
    }
    return null;
  }

  private record FixDocContext(
    @NotNull PsiElement anchor,
    @Nullable PsiComment existingComment,
    @Nullable CodeDocumentationProvider legacyProvider
  ) {
    @NotNull Language language() { return anchor.getLanguage(); }

    boolean hasExistingComment() {
      return existingComment != null && !existingComment.getTextRange().isEmpty();
    }

    static @Nullable FixDocCommentAction.FixDocContext find(@NotNull PsiElement element) {
      PsiElement documented = DocumentationStubProviderKt.findDocumentedElement(element);
      if (documented != null) {
        return new FixDocContext(documented, DocumentationStubProviderKt.findDocComment(documented), null);
      }
      CodeDocumentationProvider provider = getDocumentationProvider(element.getLanguage());
      if (provider == null) return null;
      Pair<PsiElement, PsiComment> pair = provider.parseContext(element);
      if (pair == null) return null;
      return new FixDocContext(pair.first, pair.second, provider);
    }

    boolean insertStub(@NotNull Document document, int offset) {
      if (DocumentationStubProviderKt.insertStub(anchor, document, offset)) return true;
      if (legacyProvider == null) return false;
      Pair<PsiElement, PsiComment> pair = legacyProvider.parseContext(anchor);
      if (pair == null || pair.second == null) return false;
      if (legacyProvider.insertDocumentationContentStub(pair.second, document, offset)) return true;
      String stub = legacyProvider.generateDocumentationContentStub(pair.second);
      if (stub != null) {
        document.insertString(offset, stub);
        return true;
      }
      return false;
    }

    @Nullable PsiComment findFreshComment() {
      PsiComment fresh = DocumentationStubProviderKt.findDocComment(anchor);
      if (fresh != null) return fresh;
      if (legacyProvider != null) {
        Pair<PsiElement, PsiComment> pair = legacyProvider.parseContext(anchor);
        if (pair != null) return pair.second;
      }
      return null;
    }
  }

  /**
   * Generates comment if it's not exist or try to fix if exists
   *
   * @param element     target element for which a comment should be generated
   * @param project     current project
   * @param editor      target editor
   */
  public static void generateOrFixComment(final @NotNull PsiElement element, final @NotNull Project project, final @NotNull Editor editor) {
    FixDocContext ctx = FixDocContext.find(element);
    if (ctx == null) return;
    Commenter c = LanguageCommenters.INSTANCE.forLanguage(ctx.language());
    if (!(c instanceof CodeDocumentationAwareCommenter commenter)) return;
    final Runnable task;
    if (ctx.hasExistingComment()) {
      final DocCommentFixer fixer = DocCommentFixer.EXTENSION.forLanguage(ctx.language());
      if (fixer == null) return;
      PsiComment comment = ctx.existingComment();
      if (comment == null) return;
      task = () -> fixer.fixComment(project, editor, comment);
    }
    else {
      task = () -> generateComment(ctx, editor.asModNavigator(), commenter, project);
    }
    if (!element.isPhysical()) {
      task.run();
    } else {
      final Runnable command = () -> ApplicationManager.getApplication().runWriteAction(task);
      CommandProcessor.getInstance().executeCommand(project, command, CodeInsightBundle.message("command.fix.documentation"), null);
    }
  }

  /**
   * Generates a comment if possible.
   * <p/>
   * It's assumed that this method {@link PsiDocumentManager#commitDocument(Document) syncs} all PSI-document
   * changes during the processing.
   *
   * @param ctx         context for which a comment should be generated
   * @param navigator   navigator to use
   * @param commenter   commenter to use
   * @param project     current project
   */
  private static void generateComment(@NotNull FixDocCommentAction.FixDocContext ctx,
                                      @NotNull ModNavigator navigator,
                                      @NotNull CodeDocumentationAwareCommenter commenter,
                                      @NotNull Project project) {
    PsiElement anchor = ctx.anchor();
    Document document = anchor.getContainingFile().getFileDocument();
    int commentStartOffset = anchor.getTextRange().getStartOffset();
    int lineStartOffset = document.getLineStartOffset(document.getLineNumber(commentStartOffset));
    CharSequence charSequence = document.getCharsSequence();
    if (lineStartOffset > 0 && lineStartOffset < commentStartOffset) {
      // Example:
      //    void test1() {
      //    }
      //    void test2() {
      //       <offset>
      //    }
      // We want to insert the comment at the start of the line where 'test2()' is declared.
      int nonWhiteSpaceOffset = CharArrayUtil.shiftBackward(charSequence, lineStartOffset, commentStartOffset - 1, " \t") + 1;
      commentStartOffset = Math.max(nonWhiteSpaceOffset, lineStartOffset);
    }

    int commentBodyRelativeOffset = 0;
    int caretLineOffset = 0;
    StringBuilder buffer = new StringBuilder();

    if (preferDocumentationLineComment(anchor.getContainingFile(), null)) {
      buffer.append(commenter.getDocumentationLineCommentPrefix()).append("\n");
      commentBodyRelativeOffset += Objects.requireNonNull(commenter.getDocumentationLineCommentPrefix()).length() + 1;
    }
    else {
      String commentPrefix = commenter.getDocumentationCommentPrefix();
      if (commentPrefix != null) {
        buffer.append(commentPrefix).append("\n");
        caretLineOffset++;
        commentBodyRelativeOffset += commentPrefix.length() + 1;
      }

      String linePrefix = commenter.getDocumentationCommentLinePrefix();
      if (linePrefix != null) {
        buffer.append(linePrefix);
        commentBodyRelativeOffset += linePrefix.length();
      }
      buffer.append("\n");
      commentBodyRelativeOffset++;

      String commentSuffix = commenter.getDocumentationCommentSuffix();
      if (commentSuffix != null) {
        buffer.append(commentSuffix).append("\n");
      }
    }

    if (buffer.length() <= 0) {
      return;
    }

    document.insertString(commentStartOffset, buffer);
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    docManager.commitDocument(document);

    int insertionOffset = commentStartOffset + commentBodyRelativeOffset;

    boolean inserted = ctx.insertStub(document, insertionOffset);
    if (inserted) {
      docManager.commitDocument(document);
    }

    PsiComment docComment = ctx.findFreshComment();

    navigator.moveCaretTo(commentStartOffset);
    navigator.select(TextRange.from(commentStartOffset, 0));

    if (docComment == null) {
      return;
    }

    int start = Math.min(calcStartReformatOffset(anchor), calcStartReformatOffset(docComment));
    int end = docComment.getTextRange().getEndOffset();
    reformatCommentKeepingEmptyTags(anchor.getContainingFile(), project, start, end);
    int caretOffset = document.getLineEndOffset(document.getLineNumber(navigator.getCaretOffset()) + caretLineOffset);
    navigator.moveCaretTo(caretOffset);

    if (caretOffset > 0 && caretOffset <= document.getTextLength()) {
      char c = charSequence.charAt(caretOffset - 1);
      if (!StringUtil.isWhiteSpace(c)) {
        document.insertString(caretOffset, " ");
        navigator.moveCaretTo(caretOffset + 1);
      }
    }
  }

  private static void reformatCommentKeepingEmptyTags(@NotNull PsiFile file, @NotNull Project project, int start, int end) {
    CodeStyle.runWithLocalSettings(
      project,
      CodeStyle.getSettings(file),
      tempSettings -> {
        LanguageCodeStyleSettingsProvider langProvider =
          LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
        if (langProvider != null) {
          DocCommentSettings docCommentSettings = langProvider.getDocCommentSettings(tempSettings);
          docCommentSettings.setRemoveEmptyLines(true);
          docCommentSettings.setRemoveEmptyTags(false);
        }
        CodeStyleManager.getInstance(project).reformatText(file, start, end);
      });
  }

  private static int calcStartReformatOffset(@NotNull PsiElement element) {
    int result = element.getTextRange().getStartOffset();
    for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      if (e instanceof PsiWhiteSpace) {
        result = e.getTextRange().getStartOffset();
      }
      else {
        break;
      }
    }
    return result;
  }
}

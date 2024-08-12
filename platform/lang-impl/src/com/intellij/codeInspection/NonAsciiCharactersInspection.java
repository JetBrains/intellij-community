// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.usages.ChunkExtractor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.codeInspection.options.OptPane.*;

public final class NonAsciiCharactersInspection extends LocalInspectionTool {
  public boolean CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME = true;
  public boolean CHECK_FOR_NOT_ASCII_STRING_LITERAL;
  public boolean CHECK_FOR_NOT_ASCII_COMMENT;
  public boolean CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD;

  public boolean CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME = true;
  public boolean CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING;
  public boolean CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS;
  public boolean CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD;
  public boolean CHECK_FOR_FILES_CONTAINING_BOM;

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.internationalization.issues");
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return "NonAsciiCharacters";
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    PsiFile file = session.getFile();
    if (!isFileWorthIt(file)) return PsiElementVisitor.EMPTY_VISITOR;
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.getFileType(), file.getProject(), file.getVirtualFile());
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof LeafElement)
            // optimization: ignore very frequent white space element
            || element instanceof PsiWhiteSpace) {
          return;
        }

        PsiElementKind kind = getKind(element, syntaxHighlighter);
        TextRange valueRange; // the range inside element with the actual contents with quotes/comment prefixes stripped out
        switch (kind) {
          case STRING -> {
            if (CHECK_FOR_NOT_ASCII_STRING_LITERAL || CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING) {
              String text = element.getText();
              valueRange = StringUtil.isQuotedString(text) ? new TextRange(1, text.length() - 1) : null;
              if (CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING) {
                reportMixedLanguages(element, text, holder, valueRange);
              }
              if (CHECK_FOR_NOT_ASCII_STRING_LITERAL) {
                reportNonAsciiRange(element, text, holder, valueRange);
              }
            }
          }
          case IDENTIFIER -> {
            if (CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME) {
              reportNonAsciiRange(element, element.getText(), holder, null);
            }
            if (CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME) {
              reportMixedLanguages(element, element.getText(), holder, null);
            }
          }
          case COMMENT -> {
            if (CHECK_FOR_NOT_ASCII_COMMENT || CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS) {
              String text = element.getText();
              valueRange = getCommentRange(element, text);
              if (CHECK_FOR_NOT_ASCII_COMMENT) {
                reportNonAsciiRange(element, text, holder, valueRange);
              }
              if (CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS) {
                reportMixedLanguages(element, text, holder, valueRange);
              }
            }
          }
          case OTHER -> {
            if (CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD) {
              String text = element.getText();
              iterateWordsInLeafElement(text, range -> reportMixedLanguages(element, text, holder, range));
            }
            if (CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD) {
              String text = element.getText();
              iterateWordsInLeafElement(text, range -> reportMixedLanguages(element, text, holder, range));
            }
          }
        }
      }

      @Override
      public void visitFile(@NotNull PsiFile file) {
        super.visitFile(file);
        if (CHECK_FOR_FILES_CONTAINING_BOM) {
          checkBOM(file, holder);
        }
      }
    };
  }

  private static void iterateWordsInLeafElement(@NotNull String text, @NotNull Consumer<? super TextRange> consumer) {
    int start = -1;
    int c;
    for (int i = 0; i <= text.length(); i += Character.charCount(c)) {
      c = i == text.length() ? -1 : text.codePointAt(i);
      boolean isIdentifierPart = Character.isJavaIdentifierPart(c);
      if (isIdentifierPart && start == -1) {
        start = i;
      }
      if (!isIdentifierPart && start != -1) {
        consumer.accept(new TextRange(start, i));
        start = -1;
      }
    }
  }

  // null means natural range
  private static TextRange getCommentRange(@NotNull PsiElement comment, @NotNull String text) {
    Language language = comment.getLanguage();
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    if (commenter == null) {
      return null;
    }
    for (String prefix : commenter.getLineCommentPrefixes()) {
      if (StringUtil.startsWith(text, prefix)) {
        return new TextRange(prefix.length(), text.length());
      }
    }
    String blockCommentPrefix = commenter.getBlockCommentPrefix();
    if (blockCommentPrefix != null && StringUtil.startsWith(text, blockCommentPrefix)) {
      String suffix = commenter.getBlockCommentSuffix();
      int endOffset = text.length() - (suffix != null && StringUtil.endsWith(text, blockCommentPrefix.length(), text.length(), suffix) ? suffix.length() : 0);
      return new TextRange(blockCommentPrefix.length(), endOffset);
    }
    return null;
  }

  private static void checkBOM(@NotNull PsiFile file, @NotNull ProblemsHolder holder) {
    if (file.getViewProvider().getBaseLanguage() != file.getLanguage()) {
      // don't warn multiple times on files which have multiple views like PHP and JSP
      return;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    byte[] bom = virtualFile == null ? null : virtualFile.getBOM();
    if (bom != null) {
      String hex = IntStream.range(0, bom.length)
        .map(i -> bom[i])
        .mapToObj(b -> StringUtil.toUpperCase(Integer.toString(b & 0x00ff, 16)))
        .collect(Collectors.joining());
      Charset charsetFromBOM = CharsetToolkit.guessFromBOM(bom);
      final String signature = charsetFromBOM == null
                               ? ""
                               : CodeInsightBundle.message("non.ascii.chars.inspection.message.charset.signature", charsetFromBOM.displayName());
      holder.registerProblem(file, CodeInsightBundle.message("non.ascii.chars.inspection.message.file.contains.bom", hex, signature));
    }
  }

  // if element is an identifier, return its text (its non-trivial in case of Groovy)
  private static boolean isIdentifier(@NotNull PsiElement element) {
    if (element instanceof ForeignLeafPsiElement) return false;
    PsiElement parent = element.getParent();
    PsiElement identifier;
    if (parent instanceof PsiNameIdentifierOwner &&
        (identifier = ((PsiNameIdentifierOwner)parent).getNameIdentifier()) != null) {
      // Groovy has this twisted PSI where method.getNameIdentifier() is some random light element
      String text = element.getText();
      return identifier == element || text.equals(identifier.getText());
    }
    // or it maybe the reference name
    if (parent instanceof PsiReference) {
      PsiElement refElement = ((PsiReference)parent).getElement();
      return refElement == parent || refElement == element;
    }
    return false;
  }

  private static boolean isFileWorthIt(@NotNull PsiFile file) {
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    CharSequence text = file.getViewProvider().getContents();

    Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);

    // no sense in checking transparently decoded file: all characters there are already safely encoded
    return !(charset instanceof Native2AsciiCharset);
  }

  private static boolean isAsciiCodePoint(int codePoint) {
    return codePoint < 128;
  }

  private static void reportMixedLanguages(@NotNull PsiElement element,
                                           @NotNull String text,
                                           @NotNull ProblemsHolder holder,
                                           @Nullable("null means natural range") TextRange elementRange) {
    int nonAsciiStart = -1;
    int codePoint;
    int endOffset = elementRange == null ? text.length() : elementRange.getEndOffset();
    int startOffset = elementRange == null ? 0 : elementRange.getStartOffset();

    for (int i = startOffset; i <= endOffset; i += Character.charCount(codePoint)) {
      codePoint = i == endOffset ? 0 : text.codePointAt(i);
      if (codePoint != 0 && ignoreScript(Character.UnicodeScript.of(codePoint))) {
        if (i==startOffset) startOffset++; // to calculate "isHighlightWholeWord" correctly
        continue;
      }
      if (isAsciiCodePoint(codePoint)) {
        boolean isHighlightWholeWord = i - nonAsciiStart == endOffset - startOffset;
        if (nonAsciiStart != -1 && !isHighlightWholeWord) {
          // report non-ascii range [nonAsciiStart..i) inside ascii word
          // but trim the trailing COMMON script characters first
          int j = i;
          for (; j > nonAsciiStart; j -= Character.charCount(codePoint)) {
            codePoint = text.codePointAt(j-Character.charCount(codePoint));
            if (!ignoreScript(Character.UnicodeScript.of(codePoint))) break;
          }
          
          holder.registerProblem(element, new TextRange(nonAsciiStart, j), CodeInsightBundle.message("non.ascii.chars.inspection.message.symbols.from.different.languages.found"));
          nonAsciiStart = -1;
        }
      }
      else if (nonAsciiStart == -1) {
        nonAsciiStart = i;
      }
    }
  }

  private static boolean ignoreScript(@NotNull Character.UnicodeScript script) {
    return script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED;
  }

  private static void reportNonAsciiRange(@NotNull PsiElement element,
                                          @NotNull String text,
                                          @NotNull ProblemsHolder holder,
                                          @Nullable("null means natural range") TextRange elementRange) {
    int errorCount = 0;
    int start = -1;
    int startOffset = elementRange == null ? 0 : elementRange.getStartOffset();
    int endOffset = elementRange == null ? text.length() : elementRange.getEndOffset();
    for (int i = startOffset; i <= endOffset; i++) {
      char c = i >= endOffset ? 0 : text.charAt(i);
      if (i == endOffset || c < 128) {
        if (start != -1) {
          TextRange range = new TextRange(start, i);
          holder.registerProblem(element, range, CodeInsightBundle.message("non.ascii.chars.inspection.message.non.ascii.characters"));
          start = -1;
          //do not report too many errors
          if (errorCount++ > 200) break;
        }
      }
      else if (start == -1) {
        start = i;
      }
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("CHECK_FOR_FILES_CONTAINING_BOM", CodeInsightBundle.message("non.ascii.chars.inspection.option.files.containing.bom.checkbox"))
        .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.option.files.containing.bom.label"))),
      group(CodeInsightBundle.message("non.ascii.chars.inspection.non.ascii.top.label"),
            checkbox("CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME", CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.identifiers.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.identifiers.label"))),
            checkbox("CHECK_FOR_NOT_ASCII_STRING_LITERAL", CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.strings.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.strings.label"))),
            checkbox("CHECK_FOR_NOT_ASCII_COMMENT", CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.comments.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.comments.label"))),
            checkbox("CHECK_FOR_NOT_ASCII_IN_ANY_OTHER_WORD", CodeInsightBundle.message("non.ascii.chars.inspection.option.characters.in.any.other.word.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.characters.in.any.other.word.label")))),
      group(CodeInsightBundle.message("non.ascii.chars.inspection.mixed.chars.top.label"),
            checkbox("CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME", CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.identifiers.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.identifiers.label"))),
            checkbox("CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING", CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.strings.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.string.label"))),
            checkbox("CHECK_FOR_DIFFERENT_LANGUAGES_IN_COMMENTS", CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.comments.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.comments.label"))),
            checkbox("CHECK_FOR_DIFFERENT_LANGUAGES_IN_ANY_OTHER_WORD", CodeInsightBundle.message("non.ascii.chars.inspection.option.mixed.languages.in.any.other.word.checkbox"))
              .description(HtmlChunk.raw(CodeInsightBundle.message("non.ascii.chars.inspection.example.mixed.languages.in.any.other.word.label"))))
    );
  }

  enum PsiElementKind { IDENTIFIER, STRING, COMMENT, OTHER}
  private static @NotNull PsiElementKind getKind(@NotNull PsiElement element, @Nullable SyntaxHighlighter syntaxHighlighter) {
    TextAttributesKey[] keys;
    if (element.getParent() instanceof PsiLiteralValue
        || ChunkExtractor.isHighlightedAsString(keys = syntaxHighlighter == null ? TextAttributesKey.EMPTY_ARRAY : syntaxHighlighter.getTokenHighlights(((LeafElement)element).getElementType()))) {
      return PsiElementKind.STRING;
    }
    if (element instanceof PsiComment || ChunkExtractor.isHighlightedAsComment(keys)) {
      return PsiElementKind.COMMENT;
    }
    if (isIdentifier(element)) {
      return PsiElementKind.IDENTIFIER;
    }
    return PsiElementKind.OTHER;
  }
}

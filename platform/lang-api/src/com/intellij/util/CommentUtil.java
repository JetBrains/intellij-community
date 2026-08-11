// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.CodeDocumentationAwareCommenterEx;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class CommentUtil {
  /// Should not be instantiated
  private CommentUtil() { }

  /// @param file    The file being worked on. Useful to retrieve the settings.
  /// @param comment A comment possibly targeted by an action.
  /// @return `true` if the language/comment prefers/id **line** documentation comments
  /// over **block** documentation comments.
  public static boolean preferDocumentationLineComment(@NotNull PsiFile file, @Nullable PsiDocCommentBase comment) {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(file.getLanguage());
    if (commenter instanceof CodeDocumentationAwareCommenter docCommenter) {
      if (comment == null) {
        if (docCommenter instanceof CodeDocumentationAwareCommenterEx docCommenterEx) {
          return docCommenterEx
            .shouldUseDocumentationLineComments(file, CodeStyle.getLanguageSettings(file).DOCUMENTATION_LINE_COMMENT_PREFERRED);
        }
        return CodeStyle.getLanguageSettings(file).DOCUMENTATION_LINE_COMMENT_PREFERRED;
      }
      return docCommenter.isDocumentationLineComment(comment);
    }
    // Can't guess
    return false;
  }

  /// @see #convertToDocComment(PsiElement, String, boolean, boolean)
  public static String convertToDocComment(@NotNull PsiElement context, String text) {
    return convertToDocComment(context, text, true, true);
  }

  /// @see #convertToDocComment(PsiElement, String, boolean, boolean)
  public static String convertToDocComment(@NotNull PsiElement context, String text, boolean preferSingleLine) {
    return convertToDocComment(context, text, preferSingleLine, true);
  }

  /// Convert the text to be a valid documentation comment by adding the necessary line-prefix, comment prefix and suffix
  /// depending on the user settings and `context`.
  ///
  /// Since `text` may be extracted from another comment type,
  /// it is cleaned up (leading/trailing empty lines trimmed; old doc comment prefix/suffix removed).
  ///
  /// @param context          A [PsiElement] used by [#preferDocumentationLineComment]. If the element is an instanceof [PsiDocCommentBase],
  ///                         The output text will be for a documentation comment of the same type.
  /// @param text             The input text. It may be: raw text, text extracted from another documentation comment, 
  ///                         or in extreme case an entire doc comment.
  /// @param preferSingleLine If the output is a block documentation comment spanning a single line of text, whether we prefer to be same-line of multi-line
  /// @param trim             Whether the `text` input will be trimmed.
  ///                         Setting it to `false` might be useful if you plan to insert [PsiElement]s afterward
  /// @return The text wrapped with `/* * */` or `///` (in Java)
  ///
  /// @see CommentUtil#preferDocumentationLineComment The decision logic regarding the documentation comment type
  /// @see CodeDocumentationAwareCommenter The interface defining the prefix and suffixes available
  public static String convertToDocComment(@NotNull PsiElement context, String text, boolean preferSingleLine, boolean trim) {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(context.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter codeCommenter)) {
      return text;
    }

    // Clean up the input string a bit, users may try to pass comment data of a different type than the destination one
    if (codeCommenter.getDocumentationCommentPrefix() != null) {
      text = StringUtil.trimEnd(StringUtil.trimStart(text, codeCommenter.getDocumentationCommentPrefix()),
                                Objects.requireNonNull(codeCommenter.getDocumentationCommentSuffix()));
    }
    if (trim) {
      text = StringUtil.trimLeadingLines(text);
      text = StringUtil.trimTrailingLines(text);
    }

    boolean preferLineDocs =
      preferDocumentationLineComment(context.getContainingFile(),
                                     context instanceof PsiDocCommentBase docCommentBase ? docCommentBase : null);
    String prefix =
      (preferLineDocs ? codeCommenter.getDocumentationLineCommentPrefix() : codeCommenter.getDocumentationCommentLinePrefix());
    StringBuilder sb = new StringBuilder(text.length());
    
    Pattern prefixPattern = getPrefixPattern(codeCommenter);
    String[] lines = text.split("\n");
    preferSingleLine = preferSingleLine && lines.length <= 1;

    if (!preferLineDocs) {
      sb.append(codeCommenter.getDocumentationCommentPrefix()).append(preferSingleLine ? ' ' : '\n');
    }

    // Clean up any prefix that may be left from the previous comment it originated
    // and replace it with a new one
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      Matcher matcher = prefixPattern.matcher(line);
      if (i == 0 && !preferLineDocs && preferSingleLine) {
        line = matcher.replaceAll("$1");
      }
      else {
        line = matcher.replaceAll(result -> {
          if (result.group(2) != null) { // Meaning a prefix was found on this line
            return prefix + result.group(3);
          }
          String additionalSpaces = result.group(1);
          if (additionalSpaces.isEmpty()) {
            additionalSpaces = " ";
          }
          return prefix + additionalSpaces;
        });
      }
      sb.append(line).append('\n');
    }

    if (!preferLineDocs) {
      if (preferSingleLine) {
        sb.replace(sb.length() - 1, sb.length(), "");
      }
      sb.append(" ").append(codeCommenter.getDocumentationCommentSuffix()).append("\n");
    }
    return sb.toString();
  }

  /// Build the prefix pattern from [#convertToDocComment]
  ///
  /// The generated pattern has 3 capturing groups: the spaces before the prefix, the prefix, and the spaces after that
  private static Pattern getPrefixPattern(CodeDocumentationAwareCommenter commenter) {
    // Build the pattern string to look for one or two prefixes
    String prefixPatternText = "^( *)(";
    String linePrefixBlock = commenter.getDocumentationCommentLinePrefix();
    if (linePrefixBlock != null) {
      prefixPatternText += StringUtil.escapeToRegexp(linePrefixBlock);
    }
    if (commenter.getDocumentationLineCommentPrefix() != null) {
      if (linePrefixBlock != null) {
        prefixPatternText += '|';
      }
      prefixPatternText += StringUtil.escapeToRegexp(commenter.getDocumentationLineCommentPrefix());
    }
    prefixPatternText += ")?( *)";
    return Pattern.compile(prefixPatternText);
  }
}

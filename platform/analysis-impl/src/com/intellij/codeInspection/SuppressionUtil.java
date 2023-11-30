// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SuppressionUtil extends SuppressionUtilCore {

  public static final @NonNls String FILE_PREFIX = "file:";

  /**
   * Common part of regexp for suppressing in line comments for different languages.
   * Comment start prefix isn't included, e.g. add '//' for Java/C/JS or '#' for Ruby
   */
  public static final @NonNls String COMMON_SUPPRESS_REGEXP = "\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME +
                                                      "\\s+(" + LocalInspectionTool.VALID_ID_PATTERN +
                                                      "(\\s*,\\s*" + LocalInspectionTool.VALID_ID_PATTERN + ")*)\\s*\\w*";

  public static final @NonNls Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN = Pattern.compile("//" + COMMON_SUPPRESS_REGEXP + ".*");  // for Java, C, JS line comments
  public static final Pattern SUPPRESS_IN_FILE_LINE_COMMENT_PATTERN = Pattern.compile("//" + FILE_PREFIX + COMMON_SUPPRESS_REGEXP + ".*");

  public static final @NonNls String ALL = "ALL";

  private SuppressionUtil() {
  }

  public static boolean isInspectionToolIdMentioned(@NotNull String inspectionsList, @NotNull String inspectionToolID) {
    Iterable<String> ids = StringUtil.tokenize(inspectionsList, "[, ]");

    for (@NonNls String id : ids) {
      @NonNls String trim = id.trim();
      if (trim.equals(inspectionToolID) || trim.equalsIgnoreCase(ALL)) return true;
    }
    return false;
  }

  public static @Nullable PsiElement getStatementToolSuppressedIn(@NotNull PsiElement place,
                                                                  @NotNull String toolId,
                                                                  @NotNull Class<? extends PsiElement> statementClass) {
    return getStatementToolSuppressedIn(place, toolId, statementClass, SUPPRESS_IN_LINE_COMMENT_PATTERN);
  }

  public static @Nullable PsiElement getStatementToolSuppressedIn(@NotNull PsiElement place,
                                                                  @NotNull String toolId,
                                                                  @NotNull Class<? extends PsiElement> statementClass,
                                                                  @NotNull Pattern suppressInLineCommentPattern) {
    PsiElement statement = PsiTreeUtil.getNonStrictParentOfType(place, statementClass);
    if (statement != null) {
      PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(statement);
      if (prev instanceof PsiComment) {
        String text = prev.getText();
        Matcher matcher = suppressInLineCommentPattern.matcher(text);
        if (matcher.matches() && isInspectionToolIdMentioned(matcher.group(1), toolId)) {
          return prev;
        }
      }
    }
    return null;
  }

  public static boolean isSuppressedInStatement(final @NotNull PsiElement place,
                                                final @NotNull String toolId,
                                                final @NotNull Class<? extends PsiElement> statementClass) {
    return ReadAction.compute(() -> getStatementToolSuppressedIn(place, toolId, statementClass)) != null;
  }

  public static @NotNull PsiComment createComment(@NotNull Project project,
                                                  @NotNull String commentText,
                                                  @NotNull Language language) {
    final PsiParserFacade parserFacade = PsiParserFacade.getInstance(project);
    return parserFacade.createLineOrBlockCommentFromText(language, commentText);
  }

  private static @Nullable Couple<String> getBlockPrefixSuffixPair(@NotNull PsiElement comment) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    if (commenter != null) {
      final String prefix = commenter.getBlockCommentPrefix();
      final String suffix = commenter.getBlockCommentSuffix();
      if (prefix != null || suffix != null) {
        return Couple.of(StringUtil.notNullize(prefix), StringUtil.notNullize(suffix));
      }
    }
    return null;
  }

  public static @Nullable String getLineCommentPrefix(final @NotNull PsiElement comment) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(comment.getLanguage());
    return commenter == null ? null : commenter.getLineCommentPrefix();
  }

  public static boolean isSuppressionComment(@NotNull PsiElement comment) {
    final String prefix = getLineCommentPrefix(comment);
    final String commentText = comment.getText();
    if (prefix != null) {
      return startsWithSuppressionTag(commentText, prefix);
    }
    final Couple<String> prefixSuffixPair = getBlockPrefixSuffixPair(comment);
    return prefixSuffixPair != null
           && startsWithSuppressionTag(commentText, prefixSuffixPair.first)
           && commentText.endsWith(prefixSuffixPair.second);
  }

  private static boolean startsWithSuppressionTag(@NotNull String commentText, @NotNull String prefix) {
    if (!commentText.startsWith(prefix)) {
      return false;
    }
    int index = CharArrayUtil.shiftForward(commentText, prefix.length(), " ");
    return index < commentText.length() && commentText.startsWith(SUPPRESS_INSPECTIONS_TAG_NAME, index);
  }

  public static void replaceSuppressionComment(@NotNull PsiElement comment, @NotNull String id,
                                               boolean replaceOtherSuppressionIds, @NotNull Language commentLanguage) {
    final String oldSuppressionCommentText = comment.getText();
    final String lineCommentPrefix = getLineCommentPrefix(comment);
    if (!replaceOtherSuppressionIds &&
        oldSuppressionCommentText.contains(id) &&
        StringUtil.getWordsIn(oldSuppressionCommentText).contains(id)) {
      return;
    }
    Couple<String> blockPrefixSuffix = null;
    if (lineCommentPrefix == null) {
      blockPrefixSuffix = getBlockPrefixSuffixPair(comment);
    }
    assert blockPrefixSuffix != null
           && oldSuppressionCommentText.startsWith(blockPrefixSuffix.first)
           && oldSuppressionCommentText.endsWith(blockPrefixSuffix.second)
           || lineCommentPrefix != null && oldSuppressionCommentText.startsWith(lineCommentPrefix)
      : "Unexpected suppression comment " + oldSuppressionCommentText;

    // append new suppression tool id or replace
    final String newText;
    if(replaceOtherSuppressionIds) {
      newText = SUPPRESS_INSPECTIONS_TAG_NAME + " " + id;
    }
    else if (lineCommentPrefix == null) {
      newText = oldSuppressionCommentText.substring(blockPrefixSuffix.first.length(),
                                                    oldSuppressionCommentText.length() - blockPrefixSuffix.second.length()) + "," + id;
    }
    else {
      newText = oldSuppressionCommentText.substring(lineCommentPrefix.length()) + "," + id;
    }
    comment.replace(createComment(comment.getProject(), newText, commentLanguage));
  }

  public static void createSuppression(@NotNull Project project,
                                       @NotNull PsiElement container,
                                       @NotNull String id,
                                       @NotNull Language commentLanguage) {
    final String text = SUPPRESS_INSPECTIONS_TAG_NAME + " " + id;
    PsiComment comment = createComment(project, text, commentLanguage);
    container.getParent().addBefore(comment, container);
  }

  public static boolean isSuppressed(@NotNull PsiElement psiElement, @NotNull String id) {
    for (InspectionExtensionsFactory factory : InspectionExtensionsFactory.EP_NAME.getExtensionList()) {
      if (!factory.isToCheckMember(psiElement, id)) {
        return true;
      }
    }
    return false;
  }

  public static boolean inspectionResultSuppressed(@NotNull PsiElement place, @NotNull LocalInspectionTool tool) {
    return inspectionResultSuppressed(place, (InspectionProfileEntry)tool);
  }

  public static boolean inspectionResultSuppressed(@NotNull PsiElement place, @NotNull InspectionProfileEntry tool) {
    return tool.isSuppressedFor(place);
  }
}

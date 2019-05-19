// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

/**
 * @author yole
 */
public class SuppressionUtil extends SuppressionUtilCore {
  /**
   * Common part of regexp for suppressing in line comments for different languages.
   * Comment start prefix isn't included, e.g. add '//' for Java/C/JS or '#' for Ruby
   */
  @NonNls
  public static final String COMMON_SUPPRESS_REGEXP = "\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME +
                                                      "\\s+(" + LocalInspectionTool.VALID_ID_PATTERN +
                                                      "(\\s*,\\s*" + LocalInspectionTool.VALID_ID_PATTERN + ")*)\\s*\\w*";

  @NonNls
  public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN = Pattern.compile("//" + COMMON_SUPPRESS_REGEXP + ".*");  // for Java, C, JS line comments

  @NonNls
  public static final String ALL = "ALL";

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

  @Nullable
  public static PsiElement getStatementToolSuppressedIn(@NotNull PsiElement place,
                                                        @NotNull String toolId,
                                                        @NotNull Class<? extends PsiElement> statementClass) {
    return getStatementToolSuppressedIn(place, toolId, statementClass, SUPPRESS_IN_LINE_COMMENT_PATTERN);
  }

  @Nullable
  public static PsiElement getStatementToolSuppressedIn(@NotNull PsiElement place,
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

  public static boolean isSuppressedInStatement(@NotNull final PsiElement place,
                                                @NotNull final String toolId,
                                                @NotNull final Class<? extends PsiElement> statementClass) {
    return ReadAction.compute(() -> getStatementToolSuppressedIn(place, toolId, statementClass)) != null;
  }

  @NotNull
  public static PsiComment createComment(@NotNull Project project,
                                         @NotNull String commentText,
                                         @NotNull Language language) {
    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
    return parserFacade.createLineOrBlockCommentFromText(language, commentText);
  }

  @Nullable
  private static Couple<String> getBlockPrefixSuffixPair(@NotNull PsiElement comment) {
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

  @Nullable
  public static String getLineCommentPrefix(@NotNull final PsiElement comment) {
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

  private static boolean startsWithSuppressionTag(String commentText, String prefix) {
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

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class SuppressionUtil {
  @NonNls public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";

  /**
   * Common part of regexp for suppressing in line comments for different languages.
   * Comment start prefix isn't included, e.g. add '//' for Java/C/JS or '#' for Ruby
   */
  @NonNls
  public static final String COMMON_SUPPRESS_REGEXP = "\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME +
                                                      "\\s+(" + LocalInspectionTool.VALID_ID_PATTERN +
                                                      "(\\s*,\\s*" + LocalInspectionTool.VALID_ID_PATTERN + ")*)\\s*\\w*";

  @NonNls
  public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN = Pattern.compile("//" + COMMON_SUPPRESS_REGEXP);  // for Java, C, JS line comments

  @NonNls
  public static final String ALL = "ALL";

  private SuppressionUtil() {
  }

  public static boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID) {
    Iterable<String> ids = StringUtil.tokenize(inspectionsList, "[, ]");

    for (@NonNls String id : ids) {
      @NonNls String trim = id.trim();
      if (trim.equals(inspectionToolID) || trim.equals(ALL)) return true;
    }
    return false;
  }

  @Nullable
  public static PsiElement getStatementToolSuppressedIn(final PsiElement place,
                                                        final String toolId,
                                                        final Class<? extends PsiElement> statementClass) {
    return getStatementToolSuppressedIn(place, toolId, statementClass, SUPPRESS_IN_LINE_COMMENT_PATTERN);
  }

  @Nullable
  public static PsiElement getStatementToolSuppressedIn(final PsiElement place,
                                                        final String toolId,
                                                        final Class<? extends PsiElement> statementClass,
                                                        final Pattern suppressInLineCommentPattern) {
    PsiElement statement = PsiTreeUtil.getNonStrictParentOfType(place, statementClass);
    if (statement != null) {
      PsiElement prev = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
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

  public static boolean isSuppressedInStatement(final PsiElement place,
                                                final String toolId,
                                                final Class<? extends PsiElement> statementClass) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiElement>() {
      public PsiElement compute() {
        return getStatementToolSuppressedIn(place, toolId, statementClass);
      }
    }) != null;
  }
}

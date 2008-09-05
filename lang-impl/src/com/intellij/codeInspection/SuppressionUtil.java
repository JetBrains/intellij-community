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
  @NonNls public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN =
    Pattern.compile("//\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME + "\\s+(\\w+(s*,\\w+)*)");

  private SuppressionUtil() {
  }

  public static boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID) {
    Iterable<String> ids = StringUtil.tokenize(inspectionsList, "[,]");

    for (@NonNls String id : ids) {
      if (id.equals(inspectionToolID) || id.equals("ALL")) return true;
    }
    return false;
  }

  @Nullable
  public static PsiElement getStatementToolSuppressedIn(final PsiElement place,
                                                        final String toolId,
                                                        final Class<? extends PsiElement> statementClass) {
    PsiElement statement = PsiTreeUtil.getNonStrictParentOfType(place, statementClass);
    if (statement != null) {
      PsiElement prev = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
      if (prev instanceof PsiComment) {
        String text = prev.getText();
        Matcher matcher = SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
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

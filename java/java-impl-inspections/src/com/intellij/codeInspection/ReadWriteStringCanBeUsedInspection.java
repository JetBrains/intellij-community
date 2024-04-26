// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ReadWriteStringCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher FILES_WRITE = CallMatcher.staticCall("java.nio.file.Files", "write")
    .parameterTypes("java.nio.file.Path", "byte[]", "java.nio.file.OpenOption...");
  private static final CallMatcher FILES_READ_ALL_BYTES = CallMatcher.staticCall("java.nio.file.Files", "readAllBytes")
    .parameterTypes("java.nio.file.Path");
  private static final CallMatcher STRING_GET_BYTES = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "getBytes")
    .parameterTypes("java.nio.charset.Charset");
  private static final CallMatcher CHARSET_FOR_NAME = CallMatcher.staticCall("java.nio.charset.Charset", "forName")
    .parameterTypes(CommonClassNames.JAVA_LANG_STRING);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel level = PsiUtil.getLanguageLevel(holder.getFile());
    if (level.isLessThan(LanguageLevel.JDK_11)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (FILES_WRITE.test(call)) {
          PsiMethodCallExpression bytesExpression = tryCast(ExpressionUtils.resolveExpression(call.getArgumentList().getExpressions()[1]), PsiMethodCallExpression.class);
          if (STRING_GET_BYTES.test(bytesExpression) && bytesExpression.getMethodExpression().getQualifierExpression() != null) {
            // Do not suggest for UTF-8 before Java 12 due to JDK-8209576
            ProblemHighlightType highlight;
            String message = JavaBundle.message("inspection.message.can.be.replaced.with.files.writestring");
            if (level.isAtLeast(LanguageLevel.JDK_12) || isNonUtf8Charset(bytesExpression.getArgumentList().getExpressions()[0])) {
              highlight = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
            } else {
              highlight = ProblemHighlightType.INFORMATION;
              if (!isOnTheFly) return;
            }
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
            if (referenceNameElement != null) {
              holder.registerProblem(referenceNameElement, message, highlight,
                                     new ReplaceWithWriteStringFix(highlight == ProblemHighlightType.INFORMATION));
            }
          }
        } else if (FILES_READ_ALL_BYTES.test(call)) {
          PsiExpressionList expressionList = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiExpressionList.class);
          if (expressionList != null) {
            PsiNewExpression newExpression = tryCast(expressionList.getParent(), PsiNewExpression.class);
            if (newExpression != null && newExpression.getAnonymousClass() == null &&
                ConstructionUtils.isReferenceTo(newExpression.getClassReference(), CommonClassNames.JAVA_LANG_STRING)) {
              PsiExpression[] args = expressionList.getExpressions();
              if (args.length == 2 && PsiTreeUtil.isAncestor(args[0], call, false) &&
                  TypeUtils.typeEquals("java.nio.charset.Charset", args[1].getType())) {
                final PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
                if (classReference != null) {
                  holder.registerProblem(classReference, JavaBundle.message("inspection.message.can.be.replaced.with.files.readstring"),
                                         new ReplaceWithReadStringFix());
                }
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithReadStringFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Files.readString()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiNewExpression newExpression = tryCast(element.getParent(), PsiNewExpression.class);
      if (newExpression == null) return;
      PsiExpressionList newArgList = newExpression.getArgumentList();
      if (newArgList == null) return;
      PsiExpression[] newArgs = newArgList.getExpressions();
      if (newArgs.length != 2) return;
      PsiMethodCallExpression readCall = tryCast(PsiUtil.skipParenthesizedExprDown(newArgs[0]), PsiMethodCallExpression.class);
      if (!FILES_READ_ALL_BYTES.test(readCall)) return;
      PsiExpression charsetExpression = newArgs[1];

      CommentTracker ct = new CommentTracker();
      ExpressionUtils.bindCallTo(readCall, "readString");
      if (!isUtf8Charset(charsetExpression)) {
        readCall.getArgumentList().add(ct.markUnchanged(charsetExpression));
      }
      ct.replaceAndRestoreComments(newExpression, readCall);
    }
  }

  private static final class ReplaceWithWriteStringFix extends PsiUpdateModCommandQuickFix {
    private final boolean myMayNotWork;

    private ReplaceWithWriteStringFix(boolean mayNotWork) {
      myMayNotWork = mayNotWork;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return myMayNotWork ? JavaBundle.message("quickfix.text.0.may.not.work.before.jdk.11.0.2", getFamilyName()) : getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Files.writeString()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression writeCall = tryCast(element.getParent().getParent(), PsiMethodCallExpression.class);
      if (!FILES_WRITE.test(writeCall)) return;
      PsiExpressionList argumentList = writeCall.getArgumentList();
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length < 2) return;
      PsiExpression bytesArg = args[1];
      PsiMethodCallExpression bytesExpression = tryCast(ExpressionUtils.resolveExpression(bytesArg), PsiMethodCallExpression.class);
      if (!STRING_GET_BYTES.test(bytesExpression)) return;
      PsiExpression stringExpression = PsiUtil.skipParenthesizedExprDown(bytesExpression.getMethodExpression().getQualifierExpression());
      if (stringExpression == null) return;
      PsiExpression charsetExpression = bytesExpression.getArgumentList().getExpressions()[0];

      CommentTracker ct = new CommentTracker();
      ExpressionUtils.bindCallTo(writeCall, "writeString");
      if (!isUtf8Charset(charsetExpression)) {
        argumentList.addAfter(ct.markUnchanged(charsetExpression), bytesArg);
      }
      PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(bytesArg);
      ct.replaceAndRestoreComments(bytesArg, stringExpression);
      if (variable != null) {
        ct = new CommentTracker();
        ct.markUnchanged(stringExpression);
        ct.deleteAndRestoreComments(variable);
      }
    }
  }
  static boolean isUtf8Charset(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiReferenceExpression ref) {
      if (!"UTF_8".equals(ref.getReferenceName())) {
        return false;
      }
      PsiField target = tryCast(ref.resolve(), PsiField.class);
      return target != null &&
             target.getContainingClass() != null &&
             "java.nio.charset.StandardCharsets".equals(target.getContainingClass().getQualifiedName());
    }
    if (expression instanceof PsiMethodCallExpression && CHARSET_FOR_NAME.test((PsiMethodCallExpression)expression)) {
      PsiExpression arg = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0];
      Object value = ExpressionUtils.computeConstantExpression(arg);
      return value instanceof String && ((String)value).equalsIgnoreCase("utf-8");
    }
    return false;
  }

  static boolean isNonUtf8Charset(PsiExpression expression) {
    for (int i = 0; i < 3; i++) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression instanceof PsiReferenceExpression) {
        PsiField target = tryCast(((PsiReferenceExpression)expression).resolve(), PsiField.class);
        if (target != null) {
          if ("UTF_8".equals(target.getName())) return false;
          if (target.getContainingClass() != null &&
              "java.nio.charset.StandardCharsets".equals(target.getContainingClass().getQualifiedName())) {
            return true;
          }
          if (target.hasModifierProperty(PsiModifier.FINAL)) {
            expression = target.getInitializer();
            continue;
          }
        }
      }
      if (expression instanceof PsiMethodCallExpression && CHARSET_FOR_NAME.test((PsiMethodCallExpression)expression)) {
        PsiExpression arg = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0];
        Object value = ExpressionUtils.computeConstantExpression(arg);
        return value instanceof String && !((String)value).equalsIgnoreCase("utf-8");
      }
      break;
    }
    return false;
  }
}

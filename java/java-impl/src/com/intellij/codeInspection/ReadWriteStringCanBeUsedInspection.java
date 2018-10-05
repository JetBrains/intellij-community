// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
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

public class ReadWriteStringCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher FILES_WRITE = CallMatcher.staticCall("java.nio.file.Files", "write")
    .parameterTypes("java.nio.file.Path", "byte[]", "java.nio.file.OpenOption...");
  private static final CallMatcher FILES_READ_ALL_BYTES = CallMatcher.staticCall("java.nio.file.Files", "readAllBytes")
    .parameterTypes("java.nio.file.Path");
  private static final CallMatcher STRING_GET_BYTES = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "getBytes")
    .parameterTypes("java.nio.charset.Charset");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel11OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (FILES_WRITE.test(call)) {
          PsiMethodCallExpression bytesExpression = tryCast(ExpressionUtils.resolveExpression(call.getArgumentList().getExpressions()[1]), PsiMethodCallExpression.class);
          if (STRING_GET_BYTES.test(bytesExpression) && bytesExpression.getMethodExpression().getQualifierExpression() != null) {
            holder.registerProblem(call, "Can be replaced with 'Files.writeString()'",
                                   new ReplaceWithWriteStringFix());
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
                holder.registerProblem(newExpression, "Can be replaced with 'Files.readString()'",
                                       new ReplaceWithReadStringFix());
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithReadStringFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Files.readString()");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiNewExpression newExpression = tryCast(descriptor.getStartElement(), PsiNewExpression.class);
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

  private static class ReplaceWithWriteStringFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Files.writeString()");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression writeCall = tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
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
      PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(PsiUtil.skipParenthesizedExprDown(bytesArg));
      ct.replaceAndRestoreComments(bytesArg, stringExpression);
      if (variable != null) {
        ct = new CommentTracker();
        ct.markUnchanged(stringExpression);
        ct.deleteAndRestoreComments(variable);
      }
    }
  }

  static boolean isUtf8Charset(PsiExpression expression) {
    PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
    if (ref == null) return false;
    if (!"UTF_8".equals(ref.getReferenceName())) {
      return false;
    }
    PsiField target = tryCast(ref.resolve(), PsiField.class);
    return target != null &&
           target.getContainingClass() != null &&
           "java.nio.charset.StandardCharsets".equals(target.getContainingClass().getQualifiedName());
  }
}

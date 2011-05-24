/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ConcatenationToMessageFormatAction implements IntentionAction {
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.replace.concatenation.with.formatted.output.family");
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.replace.concatenation.with.formatted.output.text");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    final PsiElement element = findElementAtCaret(editor, file);
    PsiBinaryExpression concatenation = getEnclosingLiteralConcatenation(element);
    if (concatenation == null) return;
    StringBuilder formatString = new StringBuilder();
    List<PsiExpression> args = new ArrayList<PsiExpression>();
    buildMessageFormatString(concatenation, formatString, args);

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression call = (PsiMethodCallExpression)
      factory.createExpressionFromText("java.text.MessageFormat.format()", concatenation);
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression formatArgument = factory.createExpressionFromText("\"" + formatString.toString() + "\"", null);
    argumentList.add(formatArgument);
    if (PsiUtil.isLanguageLevel5OrHigher(file)) {
      for (PsiExpression arg : args) {
        argumentList.add(arg);
      }
    }
    else {
      final PsiNewExpression arrayArg = (PsiNewExpression)factory.createExpressionFromText("new java.lang.Object[]{}", null);
      final PsiArrayInitializerExpression arrayInitializer = arrayArg.getArrayInitializer();
      assert arrayInitializer != null;
      for (PsiExpression arg : args) {
        arrayInitializer.add(arg);
      }
      argumentList.add(arrayArg);
    }
    call = (PsiMethodCallExpression) JavaCodeStyleManager.getInstance(project).shortenClassReferences(call);
    call = (PsiMethodCallExpression) element.getManager().getCodeStyleManager().reformat(call);
    concatenation.replace(call);
  }

  public static void buildMessageFormatString(PsiExpression expression,
                                              StringBuilder formatString,
                                              List<PsiExpression> args)
    throws IncorrectOperationException {
    if (expression instanceof PsiBinaryExpression) {
      final PsiType type = expression.getType();
      if (type != null && type.equalsToText("java.lang.String")
          && ((PsiBinaryExpression)expression).getOperationSign().getTokenType() == JavaTokenType.PLUS) {
        buildMessageFormatString(((PsiBinaryExpression)expression).getLOperand(), formatString, args);
        final PsiExpression rhs = ((PsiBinaryExpression)expression).getROperand();
        if (rhs != null) {
          buildMessageFormatString(rhs, formatString, args);
        }
      }
      else {
        appendArgument(args, expression, formatString);
      }
    }
    else if (expression instanceof PsiLiteralExpression) {
      final String text = String.valueOf(((PsiLiteralExpression)expression).getValue());
      formatString.append(StringUtil.escapeStringCharacters(text).replace("'", "''").replace("{", "'{'"));
    }
    else {
      appendArgument(args, expression, formatString);
    }
  }

  private static void appendArgument(List<PsiExpression> args, PsiExpression argument, StringBuilder formatString) throws IncorrectOperationException {
    formatString.append("{").append(args.size()).append("}");
    args.add(getBoxedArgument(argument));
  }

  private static PsiExpression getBoxedArgument(PsiExpression arg) throws IncorrectOperationException {
    arg = PsiUtil.deparenthesizeExpression(arg);
    assert arg != null;
    if (!PsiUtil.isLanguageLevel5OrHigher(arg)) {
      final PsiType type = arg.getType();
      if (type instanceof PsiPrimitiveType && !type.equals(PsiType.NULL)) {
        final PsiPrimitiveType primitiveType = (PsiPrimitiveType)type;
        final String boxedQName = primitiveType.getBoxedTypeName();
        if (boxedQName != null) {
          final GlobalSearchScope resolveScope = arg.getResolveScope();
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(arg.getProject());
          final PsiJavaCodeReferenceElement ref = factory.createReferenceElementByFQClassName(boxedQName, resolveScope);
          final PsiNewExpression newExpr = (PsiNewExpression)factory.createExpressionFromText("new A(b)", null);
          final PsiElement classRef = newExpr.getClassReference();
          assert classRef != null;
          classRef.replace(ref);
          final PsiExpressionList argumentList = newExpr.getArgumentList();
          assert argumentList != null;
          argumentList.getExpressions()[0].replace(arg);
          return newExpr;
        }
      }
    }
    return arg;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (PsiUtil.getLanguageLevel(file).compareTo(LanguageLevel.JDK_1_4) < 0) return false;
    final PsiElement element = findElementAtCaret(editor, file);
    PsiBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiBinaryExpression.class, false, PsiMember.class);
    if (binaryExpression == null) return false;
    final PsiType type = binaryExpression.getType();
    if (type == null) return false;
    if (!type.equalsToText("java.lang.String")) {
      return false;
    }
    return !isInsideAnnotation(binaryExpression);
  }

  private static boolean isInsideAnnotation(PsiElement element) {
    for (int i = 0; i < 20 && element instanceof PsiBinaryExpression; i++) {
      // optimization: don't check deep string concatenations more than 20 levels up.
      element = element.getParent();
      if (element instanceof PsiNameValuePair ||
          element instanceof PsiArrayInitializerMemberValue) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement findElementAtCaret(Editor editor, PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset());
  }

  @Nullable
  private static PsiBinaryExpression getEnclosingLiteralConcatenation(final PsiElement element) {
    PsiBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiBinaryExpression.class, false, PsiMember.class);
    if (binaryExpression == null) return null;
    final PsiClassType stringType = PsiType.getJavaLangString(element.getManager(), element.getResolveScope());
    if (!stringType.equals(binaryExpression.getType())) return null;
    while (true) {
      final PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiBinaryExpression)) return binaryExpression;
      final PsiBinaryExpression parentBinaryExpression = (PsiBinaryExpression)parent;
      if (!stringType.equals(parentBinaryExpression.getType())) return binaryExpression;
      binaryExpression = parentBinaryExpression;
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JavaWithTryCatchSurrounder extends JavaStatementsModCommandSurrounder {
  protected boolean myGenerateFinally;

  @Override
  public String getTemplateDescription() {
    return JavaBundle.message("surround.with.try.catch.template");
  }

  @Override
  protected void surroundStatements(@NotNull ActionContext context,
                                    @NotNull PsiElement container,
                                    @NotNull PsiElement @NotNull [] statements,
                                    @NotNull ModPsiUpdater updater) throws IncorrectOperationException {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    PsiElement[] statements1 = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements1.length == 0) return;

    DumbService.getInstance(project).runWithAlternativeResolveEnabled(() -> {
      List<PsiClassType> exceptions = getExceptionTypes(container, statements1, factory);

      @NonNls StringBuilder buffer = new StringBuilder();
      buffer.append("try{\n}");
      for (PsiClassType ignored : exceptions) {
        buffer.append("catch(Exception e){\n}");
      }
      if (myGenerateFinally) {
        buffer.append("finally{\n}");
      }
      String text = buffer.toString();
      PsiTryStatement tryStatement = (PsiTryStatement)factory.createStatementFromText(text, null);
      tryStatement = (PsiTryStatement)CodeStyleManager.getInstance(project).reformat(tryStatement);

      tryStatement = (PsiTryStatement)addAfter(tryStatement, container, statements1);

      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      SurroundWithUtil.indentCommentIfNecessary(tryBlock, statements1);
      addRangeWithinContainer(tryBlock, container, statements1, true);

      PsiCatchSection[] catchSections = tryStatement.getCatchSections();

      for (int i = 0; i < exceptions.size(); i++) {
        PsiClassType exception = exceptions.get(i);
        PsiClass target = exception.resolve();
        if (target instanceof PsiTypeParameter) {
          PsiClassType[] extendsListTypes = target.getExtendsListTypes();
          if (extendsListTypes.length > 0) {
            exception = extendsListTypes[0];
          }
        }
        String name =
          new VariableNameGenerator(tryBlock, VariableKind.PARAMETER).byName("e", "ex", "exc").byType(exception).generate(false);
        PsiCatchSection catchSection;
        try {
          catchSection = factory.createCatchSection(exception, name, tryBlock);
        }
        catch (IncorrectOperationException e) {
          updater.cancel(JavaBundle.message("surround.with.try.catch.incorrect.template.message"));
          return;
        }
        catchSection = (PsiCatchSection)catchSections[i].replace(catchSection);
        codeStyleManager.shortenClassReferences(catchSection);
      }

      container.deleteChildRange(statements1[0], statements1[statements1.length - 1]);

      PsiCodeBlock firstCatch = tryStatement.getCatchBlocks()[0];

      updater.select(SurroundWithUtil.getRangeToSelect(firstCatch));
    });
  }

  public void doSurround(ActionContext context, PsiElement element, ModPsiUpdater updater) {
    if (element instanceof PsiExpression expression) {
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(ExpressionUtils.getTopLevelExpression(expression));
      if (surrounder == null) return;
      element = surrounder.surround().getAnchor();
    } else {
      element = CommonJavaRefactoringUtil.getParentStatement(element, false);
      if (element == null) return;
    }
    surroundStatements(context, element.getParent(), new PsiElement[]{element}, updater);
  }

  private static @NotNull List<PsiClassType> getExceptionTypes(PsiElement container, PsiElement[] statements, PsiElementFactory factory) {
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
      if (exceptions.isEmpty()) {
        exceptions = Collections.singletonList(factory.createTypeByFQClassName("java.lang.Exception", container.getResolveScope()));
      }
    }
    return exceptions;
  }
}
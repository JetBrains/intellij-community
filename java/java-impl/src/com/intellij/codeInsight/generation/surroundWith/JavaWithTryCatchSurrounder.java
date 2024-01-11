// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class JavaWithTryCatchSurrounder extends JavaStatementsSurrounder {
  protected boolean myGenerateFinally;

  @Override
  public String getTemplateDescription() {
    return JavaBundle.message("surround.with.try.catch.template");
  }

  @Override
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements)
    throws IncorrectOperationException {
    try {
      return doSurround(project, container, statements);
    }
    catch (IncorrectOperationException e) {
      Messages.showErrorDialog(project, JavaBundle.message("surround.with.try.catch.incorrect.template.message"),
                               JavaBundle.message("surround.with.try.catch.incorrect.template.title"));
      return null;
    }
  }

  @Nullable
  public TextRange doSurround(Project project, PsiElement container, PsiElement[] origStatements) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    PsiElement[] statements = SurroundWithUtil.moveDeclarationsOut(container, origStatements, true);
    if (statements.length == 0) {
      return null;
    }

    return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> {
      List<PsiClassType> exceptions = getExceptionTypes(container, statements, factory);

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

      tryStatement = (PsiTryStatement)addAfter(tryStatement, container, statements);

      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      SurroundWithUtil.indentCommentIfNecessary(tryBlock, statements);
      addRangeWithinContainer(tryBlock, container, statements, true);

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
        PsiCatchSection catchSection = factory.createCatchSection(exception, name, tryBlock);
        catchSection = (PsiCatchSection)catchSections[i].replace(catchSection);
        codeStyleManager.shortenClassReferences(catchSection);
      }

      container.deleteChildRange(statements[0], statements[statements.length - 1]);

      PsiCodeBlock firstCatch = tryStatement.getCatchBlocks()[0];
      return SurroundWithUtil.getRangeToSelect(firstCatch);
    });
  }

  @NotNull
  private static List<PsiClassType> getExceptionTypes(PsiElement container, PsiElement[] statements, PsiElementFactory factory) {
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
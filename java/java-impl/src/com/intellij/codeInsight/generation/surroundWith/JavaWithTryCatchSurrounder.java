/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
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
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0) {
      return null;
    }

    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
      if (exceptions.isEmpty()) {
        exceptions = Collections.singletonList(factory.createTypeByFQClassName("java.lang.Exception", container.getResolveScope()));
      }
    }

    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("try{\n}");
    for (PsiClassType exception : exceptions) {
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
      String name = new VariableNameGenerator(tryBlock, VariableKind.PARAMETER).byType(exception).byName("e", "ex", "exc").generate(false);
      PsiCatchSection catchSection;
      try {
        catchSection = factory.createCatchSection(exception, name, tryBlock);
      }
      catch (IncorrectOperationException e) {
        Messages.showErrorDialog(project, JavaBundle.message("surround.with.try.catch.incorrect.template.message"),
                                 JavaBundle.message("surround.with.try.catch.incorrect.template.title"));
        return null;
      }
      catchSection = (PsiCatchSection)catchSections[i].replace(catchSection);
      codeStyleManager.shortenClassReferences(catchSection);
    }

    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    PsiCodeBlock firstCatch = tryStatement.getCatchBlocks()[0];
    return SurroundWithUtil.getRangeToSelect(firstCatch);
  }
}
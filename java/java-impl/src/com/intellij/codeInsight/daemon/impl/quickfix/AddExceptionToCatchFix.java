/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author mike
 */
public class AddExceptionToCatchFix extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix");
  private final boolean myUncaughtOnly;

  public AddExceptionToCatchFix(boolean uncaughtOnly) {
    myUncaughtOnly = uncaughtOnly;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();

    PsiElement element = findElement(file, offset);
    if (element == null) return;

    PsiTryStatement tryStatement = (PsiTryStatement)element.getParent();
    List<PsiClassType> unhandledExceptions = new ArrayList<>(getExceptions(element, null));
    if (unhandledExceptions.isEmpty()) return;

    ExceptionUtil.sortExceptionsByHierarchy(unhandledExceptions);

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    PsiCodeBlock catchBlockToSelect = null;

    try {
      if (tryStatement.getFinallyBlock() == null && tryStatement.getCatchBlocks().length == 0) {
        for (PsiClassType unhandledException : unhandledExceptions) {
          addCatchStatement(tryStatement, unhandledException, file);
        }
        catchBlockToSelect = tryStatement.getCatchBlocks()[0];
      }
      else {
        for (PsiClassType unhandledException : unhandledExceptions) {
          PsiCodeBlock codeBlock = addCatchStatement(tryStatement, unhandledException, file);
          if (catchBlockToSelect == null) catchBlockToSelect = codeBlock;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    if (catchBlockToSelect != null) {
      TextRange range = SurroundWithUtil.getRangeToSelect(catchBlockToSelect);
      editor.getCaretModel().moveToOffset(range.getStartOffset());

      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  @NotNull
  protected Collection<PsiClassType> getExceptions(PsiElement element, PsiElement topElement) {
    Collection<PsiClassType> exceptions = ExceptionUtil.collectUnhandledExceptions(element, topElement);
    if(!myUncaughtOnly && exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(element);
      if (exceptions.isEmpty()) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        PsiClassType exceptionType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, element.getResolveScope());
        exceptions = Collections.singleton(exceptionType);
      }
    }
    return exceptions;
  }

  private static PsiCodeBlock addCatchStatement(PsiTryStatement tryStatement,
                                                PsiClassType exceptionType,
                                                PsiFile file) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(tryStatement.getProject());

    if (tryStatement.getTryBlock() == null) {
      addTryBlock(tryStatement, factory);
    }

    String name = new VariableNameGenerator(tryStatement, VariableKind.PARAMETER).byType(exceptionType)
      .byName("e", "ex", "exception").generate(false);

    PsiCatchSection catchSection = factory.createCatchSection(exceptionType, name, tryStatement);

    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      tryStatement.add(catchSection);
    }
    else {
      tryStatement.addBefore(catchSection, getFinallySectionStart(finallyBlock));
    }

    PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
    PsiTypeElement typeElement = parameters[parameters.length - 1].getTypeElement();
    if (typeElement != null) {
      JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences(typeElement);
    }

    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    return catchBlocks[catchBlocks.length - 1];
  }

  private static void addTryBlock(PsiTryStatement tryStatement, PsiElementFactory factory) {
    PsiCodeBlock tryBlock = factory.createCodeBlock();

    PsiElement anchor;
    PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    if (catchSections.length > 0) {
      anchor = catchSections[0];
    }
    else {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      anchor = finallyBlock != null ? getFinallySectionStart(finallyBlock) : null;
    }

    if (anchor != null) {
      tryStatement.addBefore(tryBlock, anchor);
    }
    else {
      tryStatement.add(tryBlock);
    }
  }

  private static PsiElement getFinallySectionStart(@NotNull PsiCodeBlock finallyBlock) {
    PsiElement finallyElement = finallyBlock;
    while (!PsiUtil.isJavaToken(finallyElement, JavaTokenType.FINALLY_KEYWORD) && finallyElement != null) {
      finallyElement = finallyElement.getPrevSibling();
    }
    assert finallyElement != null : finallyBlock.getParent().getText();
    return finallyElement;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = findElement(file, offset);

    if (element == null) return false;

    setText(QuickFixBundle.message("add.catch.clause.text"));
    return true;
  }

  @Nullable
  private PsiElement findElement(final PsiFile file, final int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) element = file.findElementAt(offset - 1);
    if (element == null) return null;
    PsiElement parentStatement = RefactoringUtil.getParentStatement(element, false);
    if (parentStatement instanceof PsiDeclarationStatement) {
      PsiElement[] declaredElements = ((PsiDeclarationStatement)parentStatement).getDeclaredElements();
      if (declaredElements.length > 0 && declaredElements[0] instanceof PsiClass) {
        return null;
      }
    }

    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, PsiMethod.class, PsiFunctionalExpression.class);
    if (parent == null || parent instanceof PsiMethod || parent instanceof PsiFunctionalExpression) return null;
    final PsiTryStatement statement = (PsiTryStatement) parent;

    final PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      TextRange range = tryBlock.getTextRange();
      if (range.contains(offset) || range.getEndOffset() == offset) {
        if (!getExceptions(tryBlock, statement.getParent()).isEmpty()) {
          return tryBlock;
        }
      }
    }

    final PsiResourceList resourceList = statement.getResourceList();
    if (resourceList != null && resourceList.getTextRange().contains(offset)) {
      if (!getExceptions(resourceList, statement.getParent()).isEmpty()) {
        return resourceList;
      }
    }

    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.catch.clause.family");
  }
}

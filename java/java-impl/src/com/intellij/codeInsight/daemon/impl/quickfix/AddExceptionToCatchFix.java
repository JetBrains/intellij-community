// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AddExceptionToCatchFix implements ModCommandAction {
  private static final Logger LOG = Logger.getInstance(AddExceptionToCatchFix.class);
  private final boolean myUncaughtOnly;

  public AddExceptionToCatchFix(boolean uncaughtOnly) {
    myUncaughtOnly = uncaughtOnly;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    int offset = context.offset();

    PsiElement element = findElement(context.file(), offset);
    if (element == null) return ModCommand.nop();

    PsiTryStatement tryStatement = (PsiTryStatement)element.getParent();
    List<PsiClassType> unhandledExceptions = new ArrayList<>(getExceptions(element, null));
    if (unhandledExceptions.isEmpty()) return ModCommand.nop();

    ExceptionUtil.sortExceptionsByHierarchy(unhandledExceptions);
    return ModCommand.psiUpdate(tryStatement, (ts, updater) -> invoke(ts, unhandledExceptions, updater));
  }
  
  private static void invoke(@NotNull PsiTryStatement tryStatement, @NotNull List<PsiClassType> unhandledExceptions, @NotNull ModPsiUpdater updater) {
    PsiFile psiFile = tryStatement.getContainingFile();
    PsiCodeBlock catchBlockToSelect = null;

    try {
      if (tryStatement.getFinallyBlock() == null && tryStatement.getCatchBlocks().length == 0) {
        for (PsiClassType unhandledException : unhandledExceptions) {
          addCatchStatement(tryStatement, unhandledException, psiFile);
        }
        catchBlockToSelect = tryStatement.getCatchBlocks()[0];
      }
      else {
        for (PsiClassType unhandledException : unhandledExceptions) {
          PsiCodeBlock codeBlock = addCatchStatement(tryStatement, unhandledException, psiFile);
          if (catchBlockToSelect == null) catchBlockToSelect = codeBlock;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    if (catchBlockToSelect != null) {
      catchBlockToSelect = (PsiCodeBlock)CodeStyleManager.getInstance(psiFile.getProject()).reformat(catchBlockToSelect);
      TextRange range = SurroundWithUtil.getRangeToSelect(catchBlockToSelect);
      updater.select(range);
    }
  }

  protected @NotNull Collection<PsiClassType> getExceptions(PsiElement element, PsiElement topElement) {
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
                                                PsiFile psiFile) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(tryStatement.getProject());

    if (tryStatement.getTryBlock() == null) {
      addTryBlock(tryStatement, factory);
    }

    String name = new VariableNameGenerator(tryStatement, VariableKind.PARAMETER)
      .byName("e", "ex", "exception").byType(exceptionType).generate(false);

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
      JavaCodeStyleManager.getInstance(psiFile.getProject()).shortenClassReferences(typeElement);
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
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiFile psiFile = context.file();
    if (!(psiFile instanceof PsiJavaFile)) return null;

    PsiElement element = findElement(psiFile, context.offset());
    if (element == null) return null;
    
    return Presentation.of(QuickFixBundle.message("add.catch.clause.text"));
  }

  private @Nullable PsiElement findElement(final PsiFile psiFile, final int offset) {
    PsiElement element = psiFile.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) element = psiFile.findElementAt(offset - 1);
    if (element == null) return null;
    PsiElement parentStatement = CommonJavaRefactoringUtil.getParentStatement(element, false);
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
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.catch.clause.family");
  }
}

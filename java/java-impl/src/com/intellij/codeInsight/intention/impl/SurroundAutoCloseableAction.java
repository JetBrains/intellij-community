/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class SurroundAutoCloseableAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return element.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
           PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7) &&
           (findVariable(element) != null || findExpression(element) != null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiLocalVariable variable = findVariable(element);
    if (variable != null) {
      processVariable(project, editor, variable);
    }
    else {
      PsiExpression expression = findExpression(element);
      if (expression != null) {
        processExpression(project, editor, expression);
      }
    }
  }

  private static PsiLocalVariable findVariable(PsiElement element) {
    PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);

    if (variable != null &&
        variable.getParent() instanceof PsiDeclarationStatement &&
        variable.getParent().getParent() instanceof PsiCodeBlock &&
        rightType(variable.getType()) &&
        validExpression(variable.getInitializer())) {
      return variable;
    }

    if (variable == null && element instanceof PsiWhiteSpace) {
      PsiElement sibling = element.getPrevSibling();
      if (sibling instanceof PsiDeclarationStatement) {
        PsiElement lastVar = ArrayUtil.getLastElement(((PsiDeclarationStatement)sibling).getDeclaredElements());
        if (lastVar instanceof PsiLocalVariable) {
          variable = (PsiLocalVariable)lastVar;
          if (rightType(variable.getType()) && validExpression(variable.getInitializer())) {
            return variable;
          }
        }
      }
    }

    return null;
  }

  private static PsiExpression findExpression(PsiElement element) {
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);

    if (expression != null &&
        expression.getParent() instanceof PsiExpressionStatement &&
        expression.getParent().getParent() instanceof PsiCodeBlock &&
        validExpression(expression)) {
      return expression;
    }

    if (expression == null && element instanceof PsiWhiteSpace) {
      PsiElement sibling = element.getPrevSibling();
      if (sibling instanceof PsiExpressionStatement) {
        expression = ((PsiExpressionStatement)sibling).getExpression();
        if (validExpression(expression)) {
          return expression;
        }
      }
    }

    return null;
  }

  private static boolean rightType(PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  private static boolean validExpression(PsiExpression expression) {
    return expression != null &&
           rightType(expression.getType()) &&
           PsiTreeUtil.findChildOfType(expression, PsiErrorElement.class) == null;
  }

  private static void processVariable(Project project, Editor editor, PsiLocalVariable variable) {
    PsiExpression initializer = ObjectUtils.assertNotNull(variable.getInitializer());
    PsiElement declaration = variable.getParent();
    PsiElement codeBlock = declaration.getParent();

    LocalSearchScope scope = new LocalSearchScope(codeBlock);
    PsiElement last = null;
    for (PsiReference reference : ReferencesSearch.search(variable, scope).findAll()) {
      PsiElement usage = PsiTreeUtil.findPrevParent(codeBlock, reference.getElement());
      if ((last == null || usage.getTextOffset() > last.getTextOffset())) {
        last = usage;
      }
    }

    CommentTracker tracker = new CommentTracker();
    String text = "try (" + variable.getTypeElement().getText() + " " + variable.getName() + " = " + tracker.text(initializer) + ") {}";
    PsiTryStatement armStatement = (PsiTryStatement)tracker.replaceAndRestoreComments(declaration, text);

    List<PsiElement> toFormat = null;
    if (last != null) {
      toFormat = moveStatements(last, armStatement);
    }

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    PsiElement formattedElement = codeStyleManager.reformat(armStatement);
    if (toFormat != null) {
      for (PsiElement psiElement : toFormat) {
        codeStyleManager.reformat(psiElement);
      }
    }

    if (last == null) {
      PsiCodeBlock tryBlock = ((PsiTryStatement)formattedElement).getTryBlock();
      if (tryBlock != null) {
        PsiJavaToken brace = tryBlock.getLBrace();
        if (brace != null) {
          editor.getCaretModel().moveToOffset(brace.getTextOffset() + 1);
        }
      }
    }
  }

  private static List<PsiElement> moveStatements(PsiElement last, PsiTryStatement statement) {
    PsiCodeBlock tryBlock = statement.getTryBlock();
    assert tryBlock != null : statement.getText();
    PsiElement parent = statement.getParent();
    LocalSearchScope scope = new LocalSearchScope(parent);

    List<PsiElement> toFormat = new SmartList<>();
    PsiElement stopAt = last.getNextSibling();

    PsiElement i = statement.getNextSibling();
    while (i != null && i != stopAt) {
      PsiElement child = i;
      i = PsiTreeUtil.skipWhitespacesAndCommentsForward(i);

      if (!(child instanceof PsiDeclarationStatement)) continue;
      int endOffset = last.getTextRange().getEndOffset();
      //declared after last usage
      if (child.getTextOffset() > endOffset) break;

      PsiElement anchor = child;
      PsiElement[] declaredElements = ((PsiDeclarationStatement)child).getDeclaredElements();
      for (PsiElement declared : declaredElements) {
        if (!(declared instanceof PsiLocalVariable)) continue;


        boolean contained = ReferencesSearch.search(declared, scope).forEach(ref -> ref.getElement().getTextOffset() <= endOffset);

        if (!contained) {
          PsiLocalVariable var = (PsiLocalVariable)declared;
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());

          String name = var.getName();
          assert name != null : child.getText();
          PsiDeclarationStatement declarationStatement = factory.createVariableDeclarationStatement(name, var.getType(), null);
          PsiUtil.setModifierProperty((PsiLocalVariable)declarationStatement.getDeclaredElements()[0], PsiModifier.FINAL, var.hasModifierProperty(PsiModifier.FINAL));
          toFormat.add(parent.addBefore(declarationStatement, statement));

          CommentTracker commentTracker = new CommentTracker();
          PsiExpression varInit = var.getInitializer();
          if (varInit != null) {
            String varAssignText = name + " = " + commentTracker.text(varInit) + ";";
            anchor = parent.addAfter(factory.createStatementFromText(varAssignText, parent), anchor);
          }

          commentTracker.deleteAndRestoreComments(declaredElements.length == 1 ? child : var);
        }
      }

      if (child == last && !child.isValid()) {
        last = anchor;
      }
    }

    PsiElement first = statement.getNextSibling();
    tryBlock.addRangeBefore(first, last, tryBlock.getRBrace());
    parent.deleteChildRange(first, last);

    return toFormat;
  }

  private static void processExpression(Project project, Editor editor, PsiExpression expression) {
    PsiType type = ObjectUtils.assertNotNull(expression.getType());
    PsiElement statement = expression.getParent();

    CommentTracker commentTracker = new CommentTracker();
    String text = "try (" + type.getCanonicalText(true) + " r = " + commentTracker.text(expression) + ") {}";
    PsiTryStatement tryStatement = (PsiTryStatement)commentTracker.replaceAndRestoreComments(statement, text);

    tryStatement = (PsiTryStatement)CodeStyleManager.getInstance(project).reformat(tryStatement);

    tryStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(tryStatement);

    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      PsiResourceVariable var = (PsiResourceVariable)resourceList.iterator().next();
      PsiIdentifier id = var.getNameIdentifier();
      PsiExpression initializer = var.getInitializer();
      if (id != null && initializer != null) {
        type = initializer.getType();
        String[] names = IntroduceVariableBase.getSuggestedName(type, initializer).names;
        PsiType[] types = Stream.of(new TypeSelectorManagerImpl(project, type, initializer, PsiExpression.EMPTY_ARRAY).getTypesForAll())
            .filter(SurroundAutoCloseableAction::rightType)
            .toArray(PsiType[]::new);
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(var);
        builder.replaceElement(id, new NamesExpression(names));
        builder.replaceElement(var.getTypeElement(), new TypeExpression(project, types));
        builder.run(editor, true);
      }
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.surround.resource.with.ARM.block");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  private static class NamesExpression extends Expression {
    private final String[] myNames;

    public NamesExpression(String[] names) {
      myNames = names;
    }

    @Override
    public Result calculateResult(ExpressionContext context) {
      return calculateQuickResult(context);
    }

    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return new TextResult(myNames[0]);
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return Stream.of(myNames).map(LookupElementBuilder::create).toArray(LookupElement[]::new);
    }
  }

  public static class Template implements SurroundDescriptor, Surrounder {
    private Surrounder[] mySurrounders = {this};

    @NotNull
    @Override
    public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
      PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
      if (expr == null) {
        expr = findExpression(file.findElementAt(endOffset));
      }
      return expr != null && rightType(expr.getType()) ? new PsiElement[]{expr} : PsiElement.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public Surrounder[] getSurrounders() {
      return mySurrounders;
    }

    @Override
    public boolean isExclusive() {
      return false;
    }

    @Override
    public String getTemplateDescription() {
      return CodeInsightBundle.message("intention.surround.with.ARM.block.template");
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement[] elements) {
      return true;
    }

    @Nullable
    @Override
    public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) {
      if (elements.length == 1 && elements[0] instanceof PsiExpression) {
        processExpression(project, editor, (PsiExpression)elements[0]);
      }

      return null;
    }
  }
}
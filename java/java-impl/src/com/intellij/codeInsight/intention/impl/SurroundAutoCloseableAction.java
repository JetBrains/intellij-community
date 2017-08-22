/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class SurroundAutoCloseableAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    if (!PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7)) return false;

    PsiType type = null;

    PsiLocalVariable variable = findVariable(element);
    if (variable != null) {
      type = variable.getType();
    }
    else {
      PsiExpression expression = findExpression(element);
      if (expression != null) {
        type = expression.getType();
      }
    }

    return type != null && rightType(type);
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
        variable.getInitializer() != null &&
        variable.getParent() instanceof PsiDeclarationStatement &&
        variable.getParent().getParent() instanceof PsiCodeBlock) {
      return variable;
    }

    if (variable == null && element instanceof PsiWhiteSpace) {
      PsiElement sibling = element.getPrevSibling();
      if (sibling instanceof PsiDeclarationStatement) {
        PsiElement lastVar = ArrayUtil.getLastElement(((PsiDeclarationStatement)sibling).getDeclaredElements());
        if (lastVar instanceof PsiLocalVariable && ((PsiLocalVariable)lastVar).getInitializer() != null) {
          return (PsiLocalVariable)lastVar;
        }
      }
    }

    return null;
  }

  private static PsiExpression findExpression(PsiElement element) {
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);

    if (expression != null &&
        expression.getParent() instanceof PsiExpressionStatement &&
        expression.getParent().getParent() instanceof PsiCodeBlock) {
      return expression;
    }

    if (expression == null && element instanceof PsiWhiteSpace) {
      PsiElement sibling = element.getPrevSibling();
      if (sibling instanceof PsiExpressionStatement) {
        return ((PsiExpressionStatement)sibling).getExpression();
      }
    }

    return null;
  }

  private static boolean rightType(PsiType type) {
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
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

    String text = "try (" + variable.getTypeElement().getText() + " " + variable.getName() + " = " + initializer.getText() + ") {}";
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiTryStatement armStatement = (PsiTryStatement)declaration.replace(factory.createStatementFromText(text, codeBlock));

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

      PsiElement anchor = child;
      for (PsiElement declared : ((PsiDeclarationStatement)child).getDeclaredElements()) {
        if (!(declared instanceof PsiLocalVariable)) continue;

        int endOffset = last.getTextRange().getEndOffset();
        boolean contained = ReferencesSearch.search(declared, scope).forEach(ref -> ref.getElement().getTextOffset() <= endOffset);

        if (!contained) {
          PsiLocalVariable var = (PsiLocalVariable)declared;
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());

          String name = var.getName();
          assert name != null : child.getText();
          toFormat.add(parent.addBefore(factory.createVariableDeclarationStatement(name, var.getType(), null), statement));

          PsiExpression varInit = var.getInitializer();
          if (varInit != null) {
            String varAssignText = name + " = " + varInit.getText() + ";";
            anchor = parent.addAfter(factory.createStatementFromText(varAssignText, parent), anchor);
          }

          var.delete();
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
    PsiElement codeBlock = statement.getParent();

    String text = "try (" + type.getCanonicalText(true) + " r = " + expression.getText() + ") {}";
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiTryStatement tryStatement = (PsiTryStatement)statement.replace(factory.createStatementFromText(text, codeBlock));

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
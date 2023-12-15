// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.modcommand.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class SurroundAutoCloseableAction extends PsiUpdateModCommandAction<PsiElement> {
  public SurroundAutoCloseableAction() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    boolean available = element.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
                PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7) &&
                (findVariable(element) != null || findExpression(element) != null);
    return available ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiLocalVariable variable = findVariable(element);
    if (variable != null) {
      processVariable(context.project(), updater, variable);
    }
    else {
      PsiExpression expression = findExpression(element);
      if (expression != null) {
        processExpression(context.project(), updater, expression);
      }
    }
  }

  private static PsiLocalVariable findVariable(PsiElement element) {
    PsiLocalVariable variable = PsiTreeUtil.getNonStrictParentOfType(element, PsiLocalVariable.class);

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
    PsiExpression expression = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);

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
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  private static boolean validExpression(PsiExpression expression) {
    return expression != null &&
           rightType(expression.getType()) &&
           PsiTreeUtil.findChildOfType(expression, PsiErrorElement.class) == null;
  }

  private static void processVariable(Project project, ModPsiUpdater updater, PsiLocalVariable variable) {
    PsiExpression initializer = Objects.requireNonNull(variable.getInitializer());
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
    PsiTryStatement armStatement = (PsiTryStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(text, declaration);

    List<PsiElement> toFormat = null;
    if (last != null) {
      toFormat = moveStatements(last, declaration, armStatement);
    }
    armStatement = (PsiTryStatement)tracker.replaceAndRestoreComments(declaration, armStatement);

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
          updater.moveCaretTo(brace.getTextOffset() + 1);
        }
      }
    }
  }

  private static List<PsiElement> moveStatements(PsiElement last, PsiElement declaration, PsiTryStatement armStatement) {
    PsiCodeBlock tryBlock = armStatement.getTryBlock();
    assert tryBlock != null : armStatement.getText();
    PsiElement parent = declaration.getParent();
    LocalSearchScope scope = new LocalSearchScope(parent);

    List<PsiElement> toFormat = new SmartList<>();
    PsiElement stopAt = last.getNextSibling();

    PsiElement i = declaration.getNextSibling();
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


        boolean contained = ReferencesSearch.search(declared, scope).allMatch(ref -> ref.getElement().getTextOffset() <= endOffset);

        if (!contained) {
          PsiLocalVariable var = (PsiLocalVariable)declared;
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(declaration.getProject());

          String name = var.getName();
          PsiDeclarationStatement declarationStatement = factory.createVariableDeclarationStatement(name, var.getType(), null);
          PsiUtil.setModifierProperty((PsiLocalVariable)declarationStatement.getDeclaredElements()[0], PsiModifier.FINAL, var.hasModifierProperty(PsiModifier.FINAL));
          toFormat.add(parent.addBefore(declarationStatement, declaration));

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

    PsiElement first = declaration.getNextSibling();
    tryBlock.addRangeBefore(first, last, tryBlock.getRBrace());
    parent.deleteChildRange(first, last);

    return toFormat;
  }

  private static void processExpression(final Project project, ModPsiUpdater updater, PsiExpression expression) {
    PsiType type = Objects.requireNonNull(expression.getType());
    final PsiType[] types = Stream.of(new TypeSelectorManagerImpl(project, type, expression, PsiExpression.EMPTY_ARRAY).getTypesForAll())
      .filter(SurroundAutoCloseableAction::rightType)
      .toArray(PsiType[]::new);
    TypeExpression typeExpression = new TypeExpression(project, types);

    PsiStatement statement = (PsiStatement)expression.getParent();

    CommentTracker commentTracker = new CommentTracker();
    final List<String> names = new VariableNameGenerator(expression, VariableKind.LOCAL_VARIABLE).byType(type).byExpression(expression)
      .generateAll(true);
    String text = "try (" + type.getCanonicalText(true) + " " + names.get(0) + " = " + commentTracker.text(expression) + ") {}";
    PsiTryStatement tryStatement = (PsiTryStatement)commentTracker.replaceAndRestoreComments(statement, text);

    tryStatement = (PsiTryStatement)CodeStyleManager.getInstance(project).reformat(tryStatement);

    tryStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(tryStatement);

    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      final PsiResourceVariable var = (PsiResourceVariable)resourceList.iterator().next();
      final PsiIdentifier id = var.getNameIdentifier();
      PsiExpression initializer = var.getInitializer();
      if (id != null && initializer != null) {
        updater.templateBuilder()
          .field(id, new ConstantNode(var.getName()).withLookupStrings(names))
          .field(var.getTypeElement(), typeExpression);
      }
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.surround.resource.with.ARM.block");
  }

  public static final class Template implements SurroundDescriptor, Surrounder {
    private final Surrounder[] mySurrounders = {this};

    @Override
    public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
      if (!PsiUtil.isLanguageLevel7OrHigher(file)) return PsiElement.EMPTY_ARRAY;
      PsiElement element = file.findElementAt(endOffset);
      PsiElement target = findExpression(element);
      if (target == null) {
        target = findVariable(element);
      }
      return target != null ? new PsiElement[]{target} : PsiElement.EMPTY_ARRAY;
    }

    @Override
    public Surrounder @NotNull [] getSurrounders() {
      return mySurrounders;
    }

    @Override
    public boolean isExclusive() {
      return false;
    }

    @Override
    public String getTemplateDescription() {
      return JavaBundle.message("intention.surround.with.ARM.block.template");
    }

    @Override
    public boolean isApplicable(PsiElement @NotNull [] elements) {
      return elements.length == 1 && (findExpression(elements[0]) != null || findVariable(elements[0]) != null);
    }

    @Nullable
    @Override
    public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements) {
      if (elements.length == 1) {
        ActionContext context = ActionContext.from(editor, elements[0].getContainingFile());
        ModCommand command = new SurroundAutoCloseableAction().perform(context, elements[0]);
        ModCommandExecutor.getInstance().executeInteractively(context, command, editor);
      }
      return null;
    }
  }
}
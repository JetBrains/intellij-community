/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.impl.analysis.*;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.highlighting.*;
import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author cdr
 */
public class MoveInitializerToConstructorAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.constructor");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiCompiledElement) return false;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false, PsiMember.class, PsiCodeBlock.class, PsiDocComment.class);
    if (field == null || field.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!field.hasInitializer()) return false;
    PsiClass psiClass = field.getContainingClass();
    
    return psiClass != null && !psiClass.isInterface() && !(psiClass instanceof PsiAnonymousClass) && !(psiClass instanceof JspClass);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    int offset = editor.getCaretModel().getOffset();

    PsiElement element = file.findElementAt(offset);
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);

    assert field != null;
    PsiClass aClass = field.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    Collection<PsiMethod> constructorsToAddInitialization;
    if (constructors.length == 0) {
      IntentionAction addDefaultConstructorFix = QuickFixFactory.getInstance().createAddDefaultConstructorFix(aClass);
      addDefaultConstructorFix.invoke(project, editor, file);
      editor.getCaretModel().moveToOffset(offset); //restore caret
      constructorsToAddInitialization = Arrays.asList(aClass.getConstructors());
    }
    else {
      constructorsToAddInitialization = new ArrayList<PsiMethod>(Arrays.asList(constructors));
      for (Iterator<PsiMethod> iterator = constructorsToAddInitialization.iterator(); iterator.hasNext();) {
        PsiMethod ctr = iterator.next();
        List<PsiMethod> chained = HighlightControlFlowUtil.getChainedConstructors(ctr);
        if (chained != null) {
          iterator.remove();
        }
      }
    }

    PsiExpressionStatement toMove = null;
    for (PsiMethod constructor : constructorsToAddInitialization) {
      PsiCodeBlock codeBlock = constructor.getBody();
      if (codeBlock == null) {
        CreateFromUsageUtils.setupMethodBody(constructor);
        codeBlock = constructor.getBody();
      }
      PsiExpressionStatement added = addAssignment(codeBlock, field);
      if (toMove == null) toMove = added;
    }
    field.getInitializer().delete();
    if (toMove != null) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)toMove.getExpression();
      PsiExpression expression = assignment.getRExpression();
      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[] {expression}, attributes, false,null);
    }
  }

  private static PsiExpressionStatement addAssignment(@NotNull PsiCodeBlock codeBlock, @NotNull PsiField field) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(codeBlock.getProject()).getElementFactory();
    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(field.getName()+" = y;", codeBlock);
    PsiAssignmentExpression expression = (PsiAssignmentExpression)statement.getExpression();
    PsiExpression initializer = field.getInitializer();
    if (initializer instanceof PsiArrayInitializerExpression) {
      PsiType type = initializer.getType();
      PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText("new " + type.getCanonicalText() + "{}", codeBlock);
      newExpression.getArrayInitializer().replace(initializer);
      initializer = newExpression;
    }
    expression.getRExpression().replace(initializer);
    PsiStatement[] statements = codeBlock.getStatements();
    PsiElement anchor = null;
    for (PsiStatement blockStatement : statements) {
      if (blockStatement instanceof PsiExpressionStatement &&
          HighlightUtil.isSuperOrThisMethodCall(((PsiExpressionStatement)blockStatement).getExpression())) {
        continue;
      }
      if (containsReference(blockStatement, field)) {
        anchor = blockStatement;
        break;
      }
    }
    PsiElement newStatement = codeBlock.addBefore(statement,anchor);
    replaceWithQualifiedReferences(newStatement, newStatement);
    return (PsiExpressionStatement)newStatement;
  }

  private static boolean containsReference(final PsiElement element, final PsiField field) {
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == field) {
           result.set(Boolean.TRUE);
        }
        super.visitReferenceExpression(expression);
      }
    });
    return result.get().booleanValue();
  }

  private static void replaceWithQualifiedReferences(final PsiElement expression, PsiElement root) throws IncorrectOperationException {
    PsiReference reference = expression.getReference();
    if (reference != null) {
      PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiVariable && !(resolved instanceof PsiField) && !PsiTreeUtil.isAncestor(root, resolved, false)) {
        PsiVariable variable = (PsiVariable)resolved;
        PsiElementFactory factory = JavaPsiFacade.getInstance(resolved.getProject()).getElementFactory();
        PsiElement qualifiedExpr = factory.createExpressionFromText("this." + variable.getName(), expression);
        expression.replace(qualifiedExpr);
      }
    }
    else {
      for (PsiElement child : expression.getChildren()) {
        replaceWithQualifiedReferences(child, root);
      }
    }
  }
}

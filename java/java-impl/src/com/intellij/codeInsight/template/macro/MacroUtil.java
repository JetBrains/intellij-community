// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.completion.proc.VariablesProcessor;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiElementResult;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MacroUtil {
  private static final Logger LOG = Logger.getInstance(MacroUtil.class);

  @Nullable public static PsiType resultToPsiType(Result result, ExpressionContext context){
    if (result instanceof PsiTypeResult) {
      return ((PsiTypeResult) result).getType();
    }
    Project project = context.getProject();
    String text = result.toString();
    if (text == null) return null;
    PsiManager manager = PsiManager.getInstance(project);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    //-1: Hack to deal with stupid resolve
    PsiElement place = file != null ? file.findElementAt(context.getStartOffset()) : null;
    PsiDeclarationStatement decl = file != null ? PsiTreeUtil.getParentOfType(place, PsiDeclarationStatement.class) : null;
    if (decl != null) {
      place = file.findElementAt(decl.getTextOffset() -1);
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    try{
      return factory.createTypeFromText(text, place);
    }
    catch(IncorrectOperationException e){
      return null;
    }
  }

  @Nullable public static PsiExpression resultToPsiExpression(Result result, ExpressionContext context){
    if (result instanceof PsiElementResult){
      PsiElement element = ((PsiElementResult)result).getElement();
      if (element instanceof PsiExpression){
        return (PsiExpression)element;
      }
    }
    Project project = context.getProject();
    String text = result.toString();
    if (text == null) return null;
    PsiManager manager = PsiManager.getInstance(project);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    //-1: Hack to deal with resolve algorithm
    PsiElement place = file != null ? file.findElementAt(context.getStartOffset()) : null;
    if (place != null) {
      PsiElement parent = place.getParent();
      if (parent != null) {
        PsiElement parentOfParent = parent.getParent();
        if (parentOfParent instanceof PsiDeclarationStatement) {
          place = file.findElementAt(parentOfParent.getTextOffset() -1);
        }
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    try{
      return factory.createExpressionFromText(text, place);
    }
    catch(IncorrectOperationException e){
      return null;
    }
  }

  private static PsiExpression @NotNull [] getStandardExpressions(PsiElement place) {
    ArrayList<PsiExpression> array = new ArrayList<>();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());
    try {
      array.add(factory.createExpressionFromText("true", null));
      array.add(factory.createExpressionFromText("false", null));

      PsiElement scope = place;
      boolean innermostClass = true;
      while (scope != null) {
        if (scope instanceof PsiClass) {
          PsiClass aClass = (PsiClass)scope;
          String name = aClass.getName();
          if (innermostClass) {
            array.add(factory.createExpressionFromText("this", place));
          }
          else if (name != null) {
            array.add(factory.createExpressionFromText(name + ".this", place));
          }

          innermostClass = false;
          if (aClass.hasModifierProperty(PsiModifier.STATIC)) break;
        }
        else if (scope instanceof PsiMember) {
          if (((PsiMember)scope).hasModifierProperty(PsiModifier.STATIC)) break;
        }
        scope = scope.getParent();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return array.toArray(PsiExpression.EMPTY_ARRAY);
  }

  public static PsiExpression @NotNull [] getStandardExpressionsOfType(PsiElement place, PsiType type) {
    List<PsiExpression> array = new ArrayList<>();
    PsiExpression[] expressions = getStandardExpressions(place);
    for (PsiExpression expr : expressions) {
      PsiType type1 = expr.getType();
      if (type == null || type1 != null && type.isAssignableFrom(type1)) {
        array.add(expr);
      }
    }
    return array.toArray(PsiExpression.EMPTY_ARRAY);
  }

  public static PsiVariable @NotNull [] getVariablesVisibleAt(@Nullable final PsiElement place, String prefix) {
    if (place == null) {
      return new PsiVariable[0];
    }

    final Set<String> usedNames = new HashSet<>();
    final List<PsiVariable> list = new ArrayList<>();
    VariablesProcessor varproc = new VariablesProcessor(prefix, true, list) {
      @Override
      public boolean execute(@NotNull PsiElement pe, @NotNull ResolveState state) {
        if (pe instanceof PsiVariable) {
          if (!usedNames.add(((PsiVariable)pe).getName())) {
            return false;
          }
          //exclude variables that are initialized in 'place'
          final PsiExpression initializer = ((PsiVariable)pe).getInitializer();
          if (initializer != null && PsiTreeUtil.isAncestor(initializer, place, false)) return true;
        }
        return pe instanceof PsiField && !PsiUtil.isAccessible((PsiField)pe, place, null) || super.execute(pe, state);
      }
    };
    PsiScopesUtil.treeWalkUp(varproc, place, null);
    return varproc.getResultsAsArray();
  }
}

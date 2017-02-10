/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MacroUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.macro.MacroUtil");

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
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
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
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    try{
      return factory.createExpressionFromText(text, place);
    }
    catch(IncorrectOperationException e){
      return null;
    }
  }

  @NotNull private static PsiExpression[] getStandardExpressions(PsiElement place) {
    ArrayList<PsiExpression> array = new ArrayList<>();
    PsiElementFactory factory = JavaPsiFacade.getInstance(place.getProject()).getElementFactory();
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
    return array.toArray(new PsiExpression[array.size()]);
  }

  @NotNull public static PsiExpression[] getStandardExpressionsOfType(PsiElement place, PsiType type) {
    List<PsiExpression> array = new ArrayList<>();
    PsiExpression[] expressions = getStandardExpressions(place);
    for (PsiExpression expr : expressions) {
      PsiType type1 = expr.getType();
      if (type == null || type1 != null && type.isAssignableFrom(type1)) {
        array.add(expr);
      }
    }
    return array.toArray(new PsiExpression[array.size()]);
  }

  @NotNull public static PsiVariable[] getVariablesVisibleAt(@Nullable final PsiElement place, String prefix) {
    if (place == null) {
      return new PsiVariable[0];
    }

    final Set<String> usedNames = ContainerUtil.newHashSet();
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

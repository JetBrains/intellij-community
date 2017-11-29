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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.UnsupportedExpressionException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluatorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.Nullable;

public abstract class CachedEvaluator {
  private static class Cache {
    protected ExpressionEvaluator myEvaluator;
    protected EvaluateException   myException;
    protected PsiExpression       myPsiChildrenExpression;
  }
  
  SoftReference<Cache> myCache = new SoftReference<>(null);
  private TextWithImports myReferenceExpression;

  protected abstract String getClassName();

  public TextWithImports getReferenceExpression() {
    return myReferenceExpression != null ? myReferenceExpression : DebuggerUtils.getInstance().createExpressionWithImports("");
  }

  public void setReferenceExpression(TextWithImports referenceExpression) {
    myReferenceExpression = referenceExpression;
    clear();
  }

  public void clear() {
    myCache.clear();
  }

  protected Cache initEvaluatorAndChildrenExpression(final Project project) {
    final Cache cache = new Cache();
    try {
      Pair<PsiElement, PsiType> psiClassAndType = DebuggerUtilsImpl.getPsiClassAndType(getClassName(), project);
      PsiElement context = psiClassAndType.first;
      if (context == null) {
        throw EvaluateExceptionUtil.CANNOT_FIND_SOURCE_CLASS;
      }
      CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(myReferenceExpression, context);
      JavaCodeFragment codeFragment = factory.createCodeFragment(myReferenceExpression, overrideContext(context), project);
      codeFragment.setThisType(psiClassAndType.second);
      DebuggerUtils.checkSyntax(codeFragment);
      cache.myPsiChildrenExpression = codeFragment instanceof PsiExpressionCodeFragment ? ((PsiExpressionCodeFragment)codeFragment).getExpression() : null;

      try {
        cache.myEvaluator = factory.getEvaluatorBuilder().build(codeFragment, null);
      }
      catch (UnsupportedExpressionException ex) {
        ExpressionEvaluator eval = CompilingEvaluatorImpl.create(project, context, element -> codeFragment);
        if (eval != null) {
          cache.myEvaluator = eval;
        }
        throw ex;
      }
    }
    catch (EvaluateException e) {
      cache.myException = e;
    }

    myCache = new SoftReference<>(cache);
    return cache;
  }

  protected PsiElement overrideContext(PsiElement context) {
    return context;
  }

  protected ExpressionEvaluator getEvaluator(final Project project) throws EvaluateException {
    Cache cache = myCache.get();
    if(cache == null) {
      cache = PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> initEvaluatorAndChildrenExpression(project));
    }

    if(cache.myException != null) {
      throw cache.myException;
    }

    return cache.myEvaluator;
  }

  @Nullable
  protected PsiExpression getPsiExpression(final Project project) {
    Cache cache = myCache.get();
    if (cache == null) {
      cache = initEvaluatorAndChildrenExpression(project);
    }

    return cache.myPsiChildrenExpression;
  }
}

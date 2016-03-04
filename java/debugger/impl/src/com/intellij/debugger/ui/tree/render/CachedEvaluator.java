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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Dec 27, 2003
 * Time: 7:56:13 PM
 * To change this template use Options | File Templates.
 */
public abstract class CachedEvaluator {
  private final CodeFragmentFactory myDefaultFragmentFactory;

  public CachedEvaluator() {
    myDefaultFragmentFactory = new CodeFragmentFactoryContextWrapper(DefaultCodeFragmentFactory.getInstance());
  }

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
      final PsiClass contextClass = DebuggerUtils.findClass(getClassName(), project, GlobalSearchScope.allScope(project));
      if(contextClass == null) {
        throw EvaluateExceptionUtil.CANNOT_FIND_SOURCE_CLASS;
      }
      final PsiType contextType = DebuggerUtils.getType(getClassName(), project);
      cache.myPsiChildrenExpression = null;
      JavaCodeFragment codeFragment = myDefaultFragmentFactory.createCodeFragment(myReferenceExpression, contextClass, project);
      codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));
      codeFragment.setThisType(contextType);
      DebuggerUtils.checkSyntax(codeFragment);
      cache.myPsiChildrenExpression = ((PsiExpressionCodeFragment)codeFragment).getExpression();
      cache.myEvaluator = myDefaultFragmentFactory.getEvaluatorBuilder().build(cache.myPsiChildrenExpression, null);
    }
    catch (EvaluateException e) {
      cache.myException = e;
    }

    myCache = new SoftReference<>(cache);
    return cache;
  }

  protected ExpressionEvaluator getEvaluator(final Project project) throws EvaluateException {
    Cache cache = myCache.get();
    if(cache == null) {
      cache = PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Computable<Cache>() {
        public Cache compute() {
          return initEvaluatorAndChildrenExpression(project);
        }
      });
    }

    if(cache.myException != null) {
      throw cache.myException;
    }

    return cache.myEvaluator;
  }

  protected PsiExpression getPsiExpression(final Project project) {
    Cache cache = myCache.get();
    if(cache == null) {
      cache = initEvaluatorAndChildrenExpression(project);
    }

    return cache.myPsiChildrenExpression;
  }
}

package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerUtilsEx;
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
  private static class Cache {
    protected ExpressionEvaluator myEvaluator;
    protected EvaluateException   myException;
    protected PsiExpression       myPsiChildrenExpression;
  }
  
  SoftReference<Cache> myCache = new SoftReference<Cache>(null);
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
      PsiCodeFragment codeFragment = DefaultCodeFragmentFactory.getInstance().createCodeFragment(myReferenceExpression, contextClass, project);
      codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));
      codeFragment.setThisType(contextType);
      DebuggerUtils.checkSyntax(codeFragment);
      cache.myPsiChildrenExpression = ((PsiExpressionCodeFragment)codeFragment).getExpression();
      cache.myEvaluator = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).getEvaluatorBuilder().build(cache.myPsiChildrenExpression);
    }
    catch (EvaluateException e) {
      cache.myException = e;
    }

    myCache = new SoftReference<Cache>(cache);
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

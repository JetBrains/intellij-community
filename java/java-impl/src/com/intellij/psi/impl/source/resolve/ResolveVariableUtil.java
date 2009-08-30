
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.scope.util.PsiScopesUtil;

public class ResolveVariableUtil {
  public static PsiVariable resolveVariable(
          PsiJavaCodeReferenceElement ref,
          boolean[] problemWithAccess,
          boolean[] problemWithStatic
          ) {

    /*
    long time1 = System.currentTimeMillis();
    */

    final VariableResolverProcessor processor = new VariableResolverProcessor(ref);
    PsiScopesUtil.resolveAndWalk(processor, ref, null);

    /*
    long time2 = System.currentTimeMillis();
    Statistics.resolveVariableTime += (time2 - time1);
    Statistics.resolveVariableCount++;
    */
    final JavaResolveResult[] result = processor.getResult();
    if(result.length != 1) return null;
    final PsiVariable refVar = (PsiVariable) result[0].getElement();

    if (problemWithAccess != null){
      problemWithAccess[0] = !result[0].isAccessible();
    }
    if (problemWithStatic != null){
      problemWithStatic[0] = !result[0].isStaticsScopeCorrect();
    }


    return refVar;
  }
}
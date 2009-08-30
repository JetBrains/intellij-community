
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;

public class ResolveClassUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.ResolveClassUtil");

  public static PsiClass resolveClass(PsiJavaCodeReferenceElement ref) {
    if (ref instanceof PsiJavaCodeReferenceElementImpl && ((PsiJavaCodeReferenceElementImpl)ref).getKind() == PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND){
      PsiElement parent = ref.getParent();
      if (parent instanceof PsiAnonymousClass){
        parent = parent.getParent();
      }
      PsiExpression qualifier;
      if (parent instanceof PsiNewExpression){
        qualifier = ((PsiNewExpression)parent).getQualifier();
        LOG.assertTrue(qualifier != null);
      }
      else if (parent instanceof PsiJavaCodeReferenceElement){
        return null;
      }
      else{
        LOG.assertTrue(false);
        return null;
      }

      PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) return null;
      if (!(qualifierType instanceof PsiClassType)) return null;
      PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifierType);
      if (qualifierClass == null) return null;
      String name = ref.getText();
      return qualifierClass.findInnerClassByName(name, true);
    }

    final PsiElement classNameElement = ref.getReferenceNameElement();
    if (!(classNameElement instanceof PsiIdentifier)) return null;
    String className = classNameElement.getText();

    /*
    long time1 = System.currentTimeMillis();
    */

    ClassResolverProcessor processor = new ClassResolverProcessor(className, ref);
    PsiScopesUtil.resolveAndWalk(processor, ref, null);


    /*
    long time2 = System.currentTimeMillis();
    Statistics.resolveClassTime += (time2 - time1);
    Statistics.resolveClassCount++;
    */

    return processor.getResult().length == 1 ? (PsiClass)processor.getResult()[0].getElement() : null;
  }
}
/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 1:44:25 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.completion.proc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.util.ReflectionCache;

import java.util.ArrayList;
import java.util.List;


/** Simple processor to get all visible variables
 * @see com.intellij.psi.scope.util.PsiScopesUtil
 */
public class VariablesProcessor
        extends BaseScopeProcessor implements ElementClassHint{
  private final String myPrefix;
  private boolean myStaticScopeFlag = false;
  private final boolean myStaticSensitiveFlag;
  private final List<PsiVariable> myResultList;

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(String _prefix, boolean staticSensitiveFlag){
    this(_prefix, staticSensitiveFlag, new ArrayList<PsiVariable>());
  }

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(String _prefix, boolean staticSensitiveFlag, List<PsiVariable> lst){
    myPrefix = _prefix;
    myStaticSensitiveFlag = staticSensitiveFlag;
    myResultList = lst;
  }

  public boolean shouldProcess(Class elementClass) {
    return ReflectionCache.isAssignable(PsiVariable.class, elementClass);
  }

  /** Always return true since we wanna get all vars in scope */
  public boolean execute(PsiElement pe, ResolveState state){
    if(pe instanceof PsiVariable){
      final PsiVariable pvar = (PsiVariable)pe;
      final String pvar_name = pvar.getName();
      if(pvar_name.startsWith(myPrefix)){
        if(!myStaticSensitiveFlag || (!myStaticScopeFlag || pvar.hasModifierProperty(PsiModifier.STATIC))){
          myResultList.add(pvar);
        }
      }
    }

    return true;
  }

  public final void handleEvent(Event event, Object associated){
    if(event == Event.START_STATIC)
      myStaticScopeFlag = true;
  }

  /** sometimes it is important to get results as array */
  public PsiVariable[] getResultsAsArray(){
    PsiVariable[] ret = new PsiVariable[myResultList.size()];
    myResultList.toArray(ret);
    return ret;
  }
}

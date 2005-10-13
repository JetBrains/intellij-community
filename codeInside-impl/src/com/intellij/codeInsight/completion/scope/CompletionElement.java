package com.intellij.codeInsight.completion.scope;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:17:14
 * To change this template use Options | File Templates.
 */
public class CompletionElement{
  private final PsiType myQualifier;
  private final PsiElement myElement;
  private final PsiSubstitutor mySubstitutor;

  public CompletionElement(PsiType qualifier, PsiElement element, PsiSubstitutor substitutor){
    myElement = element;
    myQualifier = qualifier;
    mySubstitutor = substitutor;
  }

  public PsiSubstitutor getSubstitutor(){
    return mySubstitutor;
  }

  public PsiElement getElement(){
    return myElement;
  }

  public Object getUniqueId(){
    final String name;
    if(myElement instanceof PsiClass){
      name = ((PsiClass)myElement).getQualifiedName();
    }
    else if(myElement instanceof PsiPackage){
      name = ((PsiPackage)myElement).getQualifiedName();
    }
    else if(myElement instanceof PsiMethod){
      return ((PsiMethod)myElement).getSignature(mySubstitutor);
    }
    else if(myElement != null){
      name = PsiUtil.getName(myElement);
    }
    else{
      name = "";
    }

    return name;
  }

  public PsiType getQualifier(){
    return myQualifier;
  }
}

package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.util.PsiUtil;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:13:27
 * To change this template use Options | File Templates.
 */
public class CompletionProcessor extends BaseScopeProcessor
 implements ElementClassHint{
  private final String myPrefix;
  private boolean myStatic = false;
  private final Set<Object> myResultNames = new HashSet<Object>();
  private final List<CompletionElement> myResults;
  private final PsiElement myElement;
  private PsiElement myScope;
  private CodeInsightSettings mySettings = null;
  private final ElementFilter myFilter;
  private boolean myMembersFlag = false;
  private PsiType myQualifierType = null;
  private PsiClass myQualifierClass = null;
  private final PatternMatcher myMatcher;
  private final Pattern myPattern;

  private CompletionProcessor(String prefix, PsiElement element, List<CompletionElement> container, ElementFilter filter){
    mySettings = CodeInsightSettings.getInstance();
    myPrefix = prefix;
    myResults = container;
    myElement = element;
    myFilter = filter;
    myScope = element;
    if (ResolveUtil.isInJavaDoc(myElement))
      myMembersFlag = true;
    while(myScope != null && !(myScope instanceof PsiFile || myScope instanceof PsiClass)){
      myScope = myScope.getContext();
    }

    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        myQualifierClass = ResolveUtil.getContextClass(myElement);
        if (myQualifierClass != null) {
          myQualifierType = myElement.getManager().getElementFactory().createType(myQualifierClass);
        }
      }
      else if (qualifier != null) {
          myQualifierType = qualifier.getType();
          myQualifierClass = PsiUtil.resolveClassInType(myQualifierType);
        }
      }

    myMatcher = new Perl5Matcher();
    myPattern = CompletionUtil.createCampelHumpsMatcher(myPrefix);
  }

  public CompletionProcessor(String prefix, PsiElement element, ElementFilter filter){
    this(prefix, element, new ArrayList<CompletionElement>(), filter);
  }


  public void handleEvent(Event event, Object associated){
    if(event == Event.START_STATIC){
      myStatic = true;
    }
    if(event == Event.CHANGE_LEVEL){
      myMembersFlag = true;
    }
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor){
    if(!(element instanceof PsiClass) && element instanceof PsiModifierListOwner){
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      if(myStatic){
        if(!modifierListOwner.hasModifierProperty(PsiModifier.STATIC)){
          // we don't need non static method in static context.
          return true;
        }
      }
      else{
        if(!mySettings.SHOW_STATIC_AFTER_INSTANCE
           && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)
           && !myMembersFlag){
          // according settings we don't need to process such fields/methods
          return true;
        }
      }
    }
    final PsiElement elementParent = myElement.getParent();
    if(element instanceof PsiPackage){
      if(!mySettings.LIST_PACKAGES_IN_CODE && myScope instanceof PsiClass){
        if(!(elementParent instanceof PsiJavaCodeReferenceElement
             && ((PsiJavaCodeReferenceElement)elementParent).isQualified())) {
          return true;
        }
      }
    }

    PsiResolveHelper resolveHelper = myElement.getManager().getResolveHelper();
    if(!(element instanceof PsiMember) || resolveHelper.isAccessible((PsiMember)element, myElement, myQualifierClass)){
      final String name = PsiUtil.getName(element);
      if(name != null && (CompletionUtil.checkName(name, myPrefix) || myMatcher.matches(name, myPattern))
         && myFilter.isClassAcceptable(element.getClass())
         && myFilter.isAcceptable(new CandidateInfo(element, substitutor), myElement))
        add(new CompletionElement(myQualifierType, element, substitutor));
    }
    return true;
  }

  private void add(CompletionElement element){
    if(myResultNames.add(element.getUniqueId())){
      myResults.add(element);
    }
  }

  public void setCompletionElements(@NotNull Object[] elements) {
    for (Object element: elements) {
      myResults.add(new CompletionElement(null, element, null));
    }
  }

  public Collection<CompletionElement> getResults(){
    return myResults;
  }

  public boolean shouldProcess(Class elementClass){
    return myFilter.isClassAcceptable(elementClass);
  }
}

package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaVariableConflictResolver;
import com.intellij.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;

/**
 * @author ik, dsl
 */
public class VariableResolverProcessor extends ConflictFilterProcessor implements ElementClassHint {
  private static final ElementFilter ourFilter = ElementClassFilter.VARIABLE;

  private boolean myStaticScopeFlag = false;
  private final PsiClass myAccessClass;
  private PsiElement myCurrentFileContext = null;

  public VariableResolverProcessor(PsiJavaCodeReferenceElement place) {
    super(place.getText(), ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new SmartList<CandidateInfo>(), place);

    PsiElement referenceName = place.getReferenceNameElement();
    if (referenceName instanceof PsiIdentifier){
      setName(referenceName.getText());
    }
    PsiClass access = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      final JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression)qualifier);
      final PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
        final PsiClassType type = factory.createType((PsiTypeParameter)element);
        final PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if (accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(qualifier);
          access = factory.getArrayClass(languageLevel);
        }
        else if (accessType instanceof PsiClassType) access = ((PsiClassType)accessType).resolve();
      }
      else if (element instanceof PsiClass) access = (PsiClass)element;
    }
    myAccessClass = access;
  }

  public final void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if(event == JavaScopeProcessorEvent.START_STATIC){
      myStaticScopeFlag = true;
    }
    else if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    final boolean staticProblem = myStaticScopeFlag && !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC);
    add(new CandidateInfo(element, substitutor, myPlace, myAccessClass, staticProblem, myCurrentFileContext));
  }


  public boolean shouldProcess(DeclaractionKind kind) {
    return kind == DeclaractionKind.VARIABLE || kind == DeclaractionKind.FIELD || kind == DeclaractionKind.ENUM_CONST;
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (!(element instanceof PsiField) && (myName == null || PsiUtil.checkName(element, myName, myPlace))) {
      super.execute(element, state);
      return myResults.isEmpty();
    }

    return super.execute(element, state);
  }

  @Override
  public <T> T getHint(Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }

    return super.getHint(hintKey);
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.JavaVariableConflictResolver;
import com.intellij.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

/**
 * @author ik, dsl
 */
public class VariableResolverProcessor extends ConflictFilterProcessor implements ElementClassHint {
  private static final ElementFilter ourFilter = ElementClassFilter.VARIABLE;

  private boolean myStaticScopeFlag = false;
  private final PsiClass myAccessClass;
  private PsiElement myCurrentFileContext = null;

  public VariableResolverProcessor(@NotNull PsiJavaCodeReferenceElement place, PsiFile placeFile) {
    super(place.getText(), ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new SmartList<CandidateInfo>(), place, placeFile);

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

  @Override
  public final void handleEvent(PsiScopeProcessor.Event event, Object associated) {
    super.handleEvent(event, associated);
    if(event == JavaScopeProcessorEvent.START_STATIC){
      myStaticScopeFlag = true;
    }
    else if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  @Override
  public void add(PsiElement element, PsiSubstitutor substitutor) {
    final boolean staticProblem = myStaticScopeFlag && !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC);
    add(new CandidateInfo(element, substitutor, myPlace, myAccessClass, staticProblem, myCurrentFileContext));
  }


  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.VARIABLE || kind == DeclarationKind.FIELD || kind == DeclarationKind.ENUM_CONST;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, ResolveState state) {
    if (!(element instanceof PsiField) && (myName == null || PsiUtil.checkName(element, myName, myPlace))) {
      super.execute(element, state);
      return myResults.isEmpty();
    }

    return super.execute(element, state);
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }

    return super.getHint(hintKey);
  }
}

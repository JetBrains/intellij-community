// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.*;
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

  private @NotNull StaticScope myStaticScopeFlag = StaticScope.NON_STATIC;
  private final PsiClass myAccessClass;
  private PsiElement myCurrentFileContext;

  public VariableResolverProcessor(@NotNull PsiJavaCodeReferenceElement place, @NotNull PsiFile placeFile) {
    super(place.getReferenceName(), ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new SmartList<>(), place, placeFile);

    PsiClass access = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      final JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression)qualifier);
      final PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(placeFile.getProject());
        final PsiClassType type = factory.createType((PsiTypeParameter)element);
        final PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if (accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(placeFile);
          access = factory.getArrayClass(languageLevel);
        }
        else if (accessType instanceof PsiClassType) {
          access = ((PsiClassType)accessType).resolve();
        }
      }
      else if (element instanceof PsiClass) {
        access = (PsiClass)element;
      }
    }
    myAccessClass = access;
  }

  @Override
  protected boolean stopAtFoundResult(@NotNull JavaResolveResult cachedResult) {
    if (super.stopAtFoundResult(cachedResult)) {
      if (myPlaceFile instanceof JavaCodeFragment) {
        JavaCodeFragment.VisibilityChecker visibilityChecker = ((JavaCodeFragment)myPlaceFile).getVisibilityChecker();
        if (visibilityChecker != null) {
          PsiElement element = cachedResult.getElement();
          if (element instanceof PsiMember) {
            PsiMember member = (PsiMember)element;
            return JavaResolveUtil.isAccessible(member, member.getContainingClass(), member.getModifierList(), myPlace, myAccessClass,
                                                cachedResult.getCurrentFileResolveScope(), null);
          }
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public final void handleEvent(@NotNull PsiScopeProcessor.Event event, Object associated) {
    super.handleEvent(event, associated);
    if (JavaScopeProcessorEvent.isEnteringStaticScope(event, associated)) {
      if (myStaticScopeFlag != StaticScope.STATIC_NO_CONSTANTS) {
        myStaticScopeFlag = associated instanceof PsiClass && ((PsiClass)associated).getParent() instanceof PsiDeclarationStatement ?
                            StaticScope.STATIC_NO_CONSTANTS : StaticScope.STATIC;
      }
    }
    else if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  @Override
  public void add(@NotNull PsiElement element, @NotNull PsiSubstitutor substitutor) {
    final boolean staticProblem = isStaticProblem(element);
    add(new CandidateInfo(element, substitutor, myPlace, myAccessClass, staticProblem, myCurrentFileContext));
  }

  private boolean isStaticProblem(@NotNull PsiElement element) {
    if (myStaticScopeFlag == StaticScope.NON_STATIC || ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (element instanceof PsiField) return true;
    return !(myStaticScopeFlag == StaticScope.STATIC && element instanceof PsiVariable &&
             PsiUtil.isCompileTimeConstant((PsiVariable)element));
  }


  @Override
  public boolean shouldProcess(@NotNull DeclarationKind kind) {
    return kind == DeclarationKind.VARIABLE || kind == DeclarationKind.FIELD || kind == DeclarationKind.ENUM_CONST;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (!(element instanceof PsiField) && (myName == null || PsiUtil.checkName(element, myName, myPlace))) {
      super.execute(element, state);
      return myResults.isEmpty();
    }

    return super.execute(element, state);
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }

    return super.getHint(hintKey);
  }

  private enum StaticScope {
    NON_STATIC, STATIC, STATIC_NO_CONSTANTS
  }
}

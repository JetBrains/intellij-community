// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaModuleGraphHelper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiInferenceHelper;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.psi.impl.source.resolve.graphInference.PsiGraphInferenceHelper;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiResolveHelperImpl implements PsiResolveHelper {
  private static final Logger LOG = Logger.getInstance(PsiResolveHelperImpl.class);
  private final PsiManager myManager;

  public PsiResolveHelperImpl(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  /**
   * @deprecated Use {@link #PsiResolveHelperImpl(Project)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public PsiResolveHelperImpl(@NotNull PsiManager manager) {
    myManager = manager;
  }

  @Override
  public @NotNull JavaResolveResult resolveConstructor(PsiClassType classType, @NotNull PsiExpressionList argumentList, PsiElement place) {
    JavaResolveResult[] result = multiResolveConstructor(classType, argumentList, place);
    return result.length == 1 ? result[0] : JavaResolveResult.EMPTY;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolveConstructor(@NotNull PsiClassType type, @NotNull PsiExpressionList argumentList, @NotNull PsiElement place) {
    PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
    PsiClass aClass = classResolveResult.getElement();
    if (aClass == null) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    final MethodResolverProcessor processor;
    PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
    if (argumentList.getParent() instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymous = (PsiAnonymousClass)argumentList.getParent();
      processor = new MethodResolverProcessor(anonymous, argumentList, place, place.getContainingFile());
      aClass = anonymous.getBaseClassType().resolve();
      if (aClass == null) return JavaResolveResult.EMPTY_ARRAY;
    }
    else {
      processor = new MethodResolverProcessor(null, argumentList, place, place.getContainingFile());
    }

    ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
    PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (!processor.execute(constructor, state)) break;
    }
    if (constructors.length == 0) {
      PsiMethod defaultConstructor = LightDefaultConstructor.create(aClass);
      if (defaultConstructor != null) processor.execute(defaultConstructor, state);
    }

    return processor.getResult();
  }

  @Override
  public PsiClass resolveReferencedClass(final @NotNull String referenceText, final PsiElement context) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(myManager.getProject()).getParserFacade();
    try {
      final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(referenceText, context);
      PsiFile containingFile = ref.getContainingFile();
      LOG.assertTrue(containingFile.isValid(), referenceText);
      return ResolveClassUtil.resolveClass(ref, containingFile);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public PsiVariable resolveReferencedVariable(@NotNull String referenceText, PsiElement context) {
    return resolveVar(referenceText, context, null);
  }

  @Override
  public PsiVariable resolveAccessibleReferencedVariable(@NotNull String referenceText, PsiElement context) {
    final boolean[] problemWithAccess = new boolean[1];
    PsiVariable variable = resolveVar(referenceText, context, problemWithAccess);
    return problemWithAccess[0] ? null : variable;
  }

  private @Nullable PsiVariable resolveVar(@NotNull String referenceText, final PsiElement context, final boolean[] problemWithAccess) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(myManager.getProject()).getParserFacade();
    try {
      final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(referenceText, context);
      return ResolveVariableUtil.resolveVariable(ref, problemWithAccess, null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public boolean isAccessible(@NotNull PsiMember member, @NotNull PsiElement place, @Nullable PsiClass accessObjectClass) {
    return isAccessible(member, member.getModifierList(), place, accessObjectClass, null);
  }

  @Override
  public boolean isAccessible(@NotNull PsiMember member,
                              @Nullable PsiModifierList modifierList,
                              @NotNull PsiElement place,
                              @Nullable PsiClass accessObjectClass,
                              @Nullable PsiElement currentFileResolveScope) {
    PsiClass containingClass = member.getContainingClass();
    boolean accessible = JavaResolveUtil.isAccessible(member, containingClass, modifierList, place, accessObjectClass, currentFileResolveScope);
    if (accessible && member instanceof PsiClass && !(member instanceof PsiTypeParameter)) {
      accessible = JavaModuleGraphHelper.getInstance().isAccessible(((PsiClass)member), place);
    }
    if (fromImplicitClassOutsideThisClass(member, place)) {
      return false;
    }
    return accessible;
  }

  /**
   * Determines whether the given member is from an implicit class or not.
   * If it is from implicit class, that place is in the same class
   *
   * @param member the member to check
   * @param place  the place where the check is performed
   * @return true if the member is not from an implicit class or if place and member are both in the same implicit class, false otherwise.
   */
  private static boolean fromImplicitClassOutsideThisClass(@NotNull PsiMember member, @NotNull PsiElement place) {
    PsiImplicitClass implicitClass = PsiTreeUtil.getContextOfType(member, PsiImplicitClass.class);
    if (implicitClass == null) {
      return false;
    }
    PsiImplicitClass placeImplicitClass = PsiTreeUtil.getContextOfType(place, PsiImplicitClass.class);
    if (placeImplicitClass == null) {
      return true;
    }
    //one of them can be in the copy
    return !member.getManager().areElementsEquivalent(implicitClass, placeImplicitClass);
  }

  @Override
  public boolean isAccessible(@NotNull PsiPackage pkg, @NotNull PsiElement place) {
    return JavaModuleGraphHelper.getInstance().isAccessible(pkg.getQualifiedName(), null, place);
  }

  @Override
  public CandidateInfo @NotNull [] getReferencedMethodCandidates(@NotNull PsiCallExpression expr,
                                                                 boolean dummyImplicitConstructor,
                                                                 boolean checkVarargs) {
    PsiFile containingFile = expr.getContainingFile();
    final MethodCandidatesProcessor processor =
      new MethodCandidatesProcessor(expr, containingFile, new PsiConflictResolver[]{DuplicateConflictResolver.INSTANCE},
                                    new SmartList<>()) {
        @Override
      protected boolean acceptVarargs() {
        return checkVarargs;
      }
    };
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, expr, dummyImplicitConstructor);
    }
    catch (MethodProcessorSetupFailedException e) {
      return CandidateInfo.EMPTY_ARRAY;
    }
    return processor.getCandidates();
  }

  @Override
  public boolean hasOverloads(@NotNull PsiCallExpression call) {
    PsiFile containingFile = call.getContainingFile();
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(call, containingFile, new PsiConflictResolver[0], new SmartList<>()) {
      @Override
      protected boolean acceptVarargs() {
        return true;
      }
    };
    if (call instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)call).getMethodExpression();
      processor.setIsConstructor(false);
      processor.setName(methodExpression.getReferenceName());
      PsiScopesUtil.resolveAndWalk(processor, methodExpression, null);
    }
    else if (call instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)call).getClassOrAnonymousClassReference();
      if (classReference != null) {
        processor.setIsConstructor(true);
        processor.setName(classReference.getReferenceName());
        PsiScopesUtil.resolveAndWalk(processor, classReference, null);
      }
    }
    return processor.getCandidates().length > 1;
  }

  @Override
  public CandidateInfo @NotNull [] getReferencedMethodCandidates(@NotNull PsiCallExpression call, boolean dummyImplicitConstructor) {
    return getReferencedMethodCandidates(call, dummyImplicitConstructor, false);
  }

  @Override
  public PsiType inferTypeForMethodTypeParameter(@NotNull PsiTypeParameter typeParameter,
                                                 PsiParameter @NotNull [] parameters,
                                                 PsiExpression @NotNull [] arguments,
                                                 @NotNull PsiSubstitutor partialSubstitutor,
                                                 @Nullable PsiElement parent,
                                                 @NotNull ParameterTypeInferencePolicy policy) {
    return getInferenceHelper(PsiUtil.getLanguageLevel(parent != null ? parent : typeParameter))
      .inferTypeForMethodTypeParameter(typeParameter, parameters, arguments, partialSubstitutor, parent, policy);
  }

  @Override
  public @NotNull PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                                    PsiParameter @NotNull [] parameters,
                                                    PsiExpression @NotNull [] arguments,
                                                    @NotNull PsiSubstitutor partialSubstitutor,
                                                    @NotNull PsiElement parent,
                                                    @NotNull ParameterTypeInferencePolicy policy) {
    return getInferenceHelper(PsiUtil.getLanguageLevel(parent))
      .inferTypeArguments(typeParameters, parameters, arguments, null, partialSubstitutor, parent, policy, PsiUtil.getLanguageLevel(parent));
  }

  @Override
  public @NotNull PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                                    PsiParameter @NotNull [] parameters,
                                                    PsiExpression @NotNull [] arguments,
                                                    @NotNull MethodCandidateInfo currentCandidate,
                                                    @NotNull PsiElement parent,
                                                    @NotNull ParameterTypeInferencePolicy policy,
                                                    @NotNull LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel)
      .inferTypeArguments(typeParameters, parameters, arguments, currentCandidate, currentCandidate.getSiteSubstitutor(), parent, policy, languageLevel);
  }

  @Override
  public @NotNull PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                                    PsiType @NotNull [] leftTypes,
                                                    PsiType @NotNull [] rightTypes,
                                                    @NotNull LanguageLevel languageLevel) {
    return inferTypeArguments(typeParameters, leftTypes, rightTypes, PsiSubstitutor.EMPTY, languageLevel);
  }

  @Override
  public @NotNull PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                                    PsiType @NotNull [] leftTypes,
                                                    PsiType @NotNull [] rightTypes,
                                                    @NotNull PsiSubstitutor partialSubstitutor,
                                                    @NotNull LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel)
      .inferTypeArguments(typeParameters, leftTypes, rightTypes, partialSubstitutor, languageLevel);
  }

  @Override
  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                 PsiType param,
                                                 PsiType arg,
                                                 boolean isContraVariantPosition,
                                                 LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel)
      .getSubstitutionForTypeParameter(typeParam, param, arg, isContraVariantPosition, languageLevel);
  }

  @Override
  public @NotNull LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile) {
    return JavaPsiImplementationHelper.getInstance(myManager.getProject()).getEffectiveLanguageLevel(virtualFile);
  }

  public @NotNull PsiInferenceHelper getInferenceHelper(@NotNull LanguageLevel languageLevel) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      return new PsiGraphInferenceHelper(myManager);
    }
    return new PsiOldInferenceHelper(myManager);
  }
}

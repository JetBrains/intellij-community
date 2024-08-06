// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.daemon.impl.analysis.PsiMethodReferenceHighlightingUtil;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstanceBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.pom.java.JavaFeature.PATTERNS_IN_SWITCH;

public final class JavaCompletionProcessor implements PsiScopeProcessor, ElementClassHint {
  private static final Logger LOG = Logger.getInstance(JavaCompletionProcessor.class);

  private final boolean myInJavaDoc;
  private boolean myStatic;
  private PsiElement myDeclarationHolder;
  private final Map<CompletionElement, CompletionElement> myResults = new LinkedHashMap<>();
  private final Set<CompletionElement> mySecondRateResults = new ReferenceOpenHashSet<>();
  private final Set<String> myShadowedNames = new HashSet<>();
  private final Set<String> myCurrentScopeMethodNames = new HashSet<>();
  private final Set<String> myFinishedScopesMethodNames = new HashSet<>();
  private final PsiElement myElement;
  private final PsiElement myScope;
  private final ElementFilter myFilter;
  private boolean myMembersFlag;
  private final boolean myQualified;
  private PsiType myQualifierType;
  private final Condition<? super String> myMatcher;
  private final Options myOptions;
  private final boolean myAllowStaticWithInstanceQualifier;
  private final NotNullLazyValue<Collection<PsiType>> myExpectedGroundTypes;

  public JavaCompletionProcessor(@NotNull PsiElement element, @NotNull ElementFilter filter, @NotNull Options options, @NotNull Condition<? super String> nameCondition) {
    myOptions = options;
    myElement = element;
    myMatcher = nameCondition;
    myFilter = filter;
    PsiElement scope = element;
    myInJavaDoc = JavaResolveUtil.isInJavaDoc(myElement);
    if (myInJavaDoc) myMembersFlag = true;
    while(scope != null && !(scope instanceof PsiFile) && !(scope instanceof PsiClass)){
      scope = scope.getContext();
    }
    myScope = scope;

    PsiClass qualifierClass = null;
    PsiElement elementParent = element.getContext();
    myQualified = elementParent instanceof PsiReferenceExpression && ((PsiReferenceExpression)elementParent).isQualified();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        final PsiJavaCodeReferenceElement qSuper = ((PsiSuperExpression)qualifier).getQualifier();
        if (qSuper == null) {
          qualifierClass = JavaResolveUtil.getContextClass(myElement);
        } else {
          final PsiElement target = qSuper.resolve();
          qualifierClass = target instanceof PsiClass ? (PsiClass)target : null;
        }
      }
      else if (qualifier != null) {
        myQualifierType = qualifier.getType();
        if (myQualifierType == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (target instanceof PsiClass) {
            qualifierClass = (PsiClass)target;
          }
        }
      } else {
        qualifierClass = JavaResolveUtil.getContextClass(myElement);
      }
    }
    if (qualifierClass != null) {
      myQualifierType = JavaPsiFacade.getElementFactory(element.getProject()).createType(qualifierClass);
    }

    myAllowStaticWithInstanceQualifier = !options.filterStaticAfterInstance || allowStaticAfterInstanceQualifier(element);
    myExpectedGroundTypes = NotNullLazyValue.createValue(
      () -> ContainerUtil.map(ExpectedTypesGetter.getExpectedTypes(element, false),
                              FunctionalInterfaceParameterizationUtil::getGroundTargetType));
  }

  private static boolean allowStaticAfterInstanceQualifier(@NotNull PsiElement position) {
    return SuppressManager.getInstance().isSuppressedFor(position, AccessStaticViaInstanceBase.ACCESS_STATIC_VIA_INSTANCE) ||
           Registry.is("ide.java.completion.suggest.static.after.instance");
  }

  @ApiStatus.Internal
  public static boolean seemsInternal(@NotNull PsiClass clazz) {
    String name = clazz.getName();
    return name != null && name.startsWith("$");
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated){
    if (JavaScopeProcessorEvent.isEnteringStaticScope(event, associated)) {
      myStatic = true;
    }
    if(event == JavaScopeProcessorEvent.CHANGE_LEVEL){
      myMembersFlag = true;
      myFinishedScopesMethodNames.addAll(myCurrentScopeMethodNames);
      myCurrentScopeMethodNames.clear();
    }
    if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myDeclarationHolder = (PsiElement)associated;
    }
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof PsiPackage && !isQualifiedContext()) {
      if (myScope instanceof PsiClass) {
        return true;
      }
      if (((PsiPackage)element).getQualifiedName().contains(".") &&
          PsiTreeUtil.getParentOfType(myElement, PsiImportStatementBase.class) != null) {
        return true;
      }
    }

    if (element instanceof PsiClass clazz) {
      if (seemsInternal(clazz)) {
        return true;
      }
      if (myOptions.instantiableOnly && clazz.hasModifierProperty(PsiModifier.ABSTRACT) && clazz.hasModifierProperty(PsiModifier.SEALED)) {
        return true;
      }
    }

    if (element instanceof PsiImplicitClass) {
      return true;
    }

    if (element instanceof PsiMember && !PsiNameHelper.getInstance(element.getProject()).isIdentifier(((PsiMember)element).getName())) {
      // The member could be defined in another JVM language where its name is not a legal name in Java.
      // In this case, just skip such the member. We cannot legally reference it from Java source.
      return true;
    }

    if (element instanceof PsiMethod method && PsiTypesUtil.isGetClass(method) && PsiUtil.isAvailable(JavaFeature.GENERICS, myElement)) {
      PsiType patchedType = PsiTypesUtil.createJavaLangClassType(myElement, myQualifierType, false);
      if (patchedType != null) {
        element = new LightMethodBuilder(element.getManager(), method.getName()).
          addModifier(PsiModifier.PUBLIC).
          setMethodReturnType(patchedType).
          setContainingClass(method.getContainingClass());
      }
    }

    if (element instanceof PsiVariable) {
      String name = ((PsiVariable)element).getName();
      if (myShadowedNames.contains(name)) return true;
      if (myQualified || PsiUtil.isJvmLocalVariable(element)) {
        myShadowedNames.add(name);
      }
    }

    if (element instanceof PsiMethod) {
      myCurrentScopeMethodNames.add(((PsiMethod)element).getName());
    }

    if (!satisfies(element, state) || !isAccessible(element)) return true;

    StaticProblem sp = myElement.getParent() instanceof PsiMethodReferenceExpression ? StaticProblem.none : getStaticProblem(element);
    if (sp == StaticProblem.instanceAfterStatic) return true;

    CompletionElement completion = new CompletionElement(
      element, state.get(PsiSubstitutor.KEY), getCallQualifierText(element), getMethodReferenceType(element));
    CompletionElement prev = myResults.get(completion);
    if (prev == null || completion.isMoreSpecificThan(prev)) {
      myResults.put(completion, completion);
      if (sp == StaticProblem.staticAfterInstance) {
        mySecondRateResults.add(completion);
      }
    }

    if (!(element instanceof PsiClass psiClass) || !PsiUtil.isAvailable(PATTERNS_IN_SWITCH, myElement)) return true;

    if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
      addSealedHierarchy(state, psiClass);
    }
    return true;
  }

  @Contract(pure = true)
  private void addSealedHierarchy(@NotNull ResolveState state, @NotNull PsiClass psiClass) {
    final Collection<PsiClass> sealedInheritors = SealedUtils.findSameFileInheritorsClasses(psiClass);
    for (PsiClass inheritor : sealedInheritors) {
      final CompletionElement completion = new CompletionElement(inheritor, state.get(PsiSubstitutor.KEY));
      final CompletionElement prev = myResults.get(completion);

      if (prev == null || completion.isMoreSpecificThan(prev)) {
        myResults.put(completion, completion);
      }
    }
  }

  private @Nullable PsiType getMethodReferenceType(PsiElement completion) {
    PsiElement parent = myElement.getParent();
    if (completion instanceof PsiMethod && parent instanceof PsiMethodReferenceExpression) {
      PsiType matchingType = ContainerUtil.find(myExpectedGroundTypes.getValue(), candidate ->
        candidate != null && hasSuitableType((PsiMethodReferenceExpression)parent, (PsiMethod)completion, candidate));
      return matchingType != null ? matchingType : new PsiMethodReferenceType((PsiMethodReferenceExpression)parent);
    }
    return null;
  }

  private static boolean hasSuitableType(PsiMethodReferenceExpression refPlace, PsiMethod method, @NotNull PsiType expectedType) {
    PsiMethodReferenceExpression referenceExpression = createMethodReferenceExpression(method, refPlace);
    return LambdaUtil.performWithTargetType(referenceExpression, expectedType, () -> {
      JavaResolveResult result = referenceExpression.advancedResolve(false);
      return method.getManager().areElementsEquivalent(method, result.getElement()) &&
             PsiMethodReferenceUtil.isReturnTypeCompatible(referenceExpression, result, expectedType) &&
             PsiMethodReferenceHighlightingUtil.checkMethodReferenceContext(referenceExpression, method, expectedType) == null;
    });
  }

  private static PsiMethodReferenceExpression createMethodReferenceExpression(PsiMethod method, PsiMethodReferenceExpression place) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    PsiMethodReferenceExpression copy = (PsiMethodReferenceExpression)place.copy();
    PsiElement referenceNameElement = copy.getReferenceNameElement();
    LOG.assertTrue(referenceNameElement != null, copy);
    referenceNameElement.replace(method.isConstructor() ? factory.createKeyword("new") : factory.createIdentifier(method.getName()));
    return copy;
  }

  private @NotNull String getCallQualifierText(@NotNull PsiElement element) {
    if (element instanceof PsiMethod method && myFinishedScopesMethodNames.contains(method.getName())) {
      String className = myDeclarationHolder instanceof PsiClass psiClass ? psiClass.getName() : null;
      if (className != null) {
        return className + (method.hasModifierProperty(PsiModifier.STATIC) ? "." : ".this.");
      }
    }
    return "";
  }

  private boolean isQualifiedContext() {
    final PsiElement elementParent = myElement.getParent();
    return elementParent instanceof PsiQualifiedReference && ((PsiQualifiedReference)elementParent).getQualifier() != null;
  }

  private StaticProblem getStaticProblem(@NotNull PsiElement element) {
    if (myOptions.showInstanceInStaticContext && !isQualifiedContext()) {
      return StaticProblem.none;
    }
    if (element instanceof PsiModifierListOwner modifierListOwner) {
      if (myStatic) {
        if (!(element instanceof PsiClass) && !modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
          // we don't need non-static method in static context.
          return StaticProblem.instanceAfterStatic;
        }
      }
      else {
        if (!myAllowStaticWithInstanceQualifier
            && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)
            && !myMembersFlag) {
          // according settings we don't need to process such fields/methods
          return StaticProblem.staticAfterInstance;
        }
      }
    }
    return StaticProblem.none;
  }

  public boolean satisfies(@NotNull PsiElement element, @NotNull ResolveState state) {
    String name = PsiUtilCore.getName(element);
    if (element instanceof PsiMethod &&
        ((PsiMethod)element).isConstructor() &&
        myElement.getParent() instanceof PsiMethodReferenceExpression) {
      name = PsiKeyword.NEW;
    }
    return StringUtil.isNotEmpty(name) &&
           myMatcher.value(name) &&
           myFilter.isClassAcceptable(element.getClass()) &&
           myFilter.isAcceptable(new CandidateInfo(element, state.get(PsiSubstitutor.KEY)), myElement);
  }

  public void setQualifierType(@Nullable PsiType qualifierType) {
    myQualifierType = qualifierType;
  }

  public @Nullable PsiType getQualifierType() {
    return myQualifierType;
  }

  public boolean isAccessible(final @Nullable PsiElement element) {
    // if checkAccess is false, we only show inaccessible source elements because their access modifiers can be changed later by the user.
    // compiled element can't be changed, so we don't pollute the completion with them. In Javadoc, everything is allowed.
    if (!myOptions.checkAccess && myInJavaDoc) return true;

    if (isAccessibleForResolve(element)) {
      return true;
    }
    return !myOptions.checkAccess && !(element instanceof PsiCompiledElement);
  }

  private boolean isAccessibleForResolve(@Nullable PsiElement element) {
    if (element instanceof PsiMember member) {
      Set<PsiClass> accessObjectClasses =
        !myQualified ? Collections.singleton(null) :
        myQualifierType instanceof PsiIntersectionType ? ContainerUtil.map2Set(((PsiIntersectionType)myQualifierType).getConjuncts(),
                                                                               PsiUtil::resolveClassInClassTypeOnly) :
        Collections.singleton(PsiUtil.resolveClassInClassTypeOnly(myQualifierType));
      PsiResolveHelper helper = getResolveHelper();
      PsiModifierList modifierList = member.getModifierList();
      return ContainerUtil.exists(accessObjectClasses, aoc ->
        helper.isAccessible(member, modifierList, myElement, aoc, myDeclarationHolder));
    }
    if (element instanceof PsiPackage) {
      return getResolveHelper().isAccessible((PsiPackage)element, myElement);
    }
    return true;
  }

  private @NotNull PsiResolveHelper getResolveHelper() {
    return JavaPsiFacade.getInstance(myElement.getProject()).getResolveHelper();
  }

  public void setCompletionElements(Object @NotNull [] elements) {
    for (Object element: elements) {
      CompletionElement completion = new CompletionElement(element, PsiSubstitutor.EMPTY);
      myResults.put(completion, completion);
    }
  }

  public Iterable<CompletionElement> getResults() {
    if (mySecondRateResults.size() == myResults.size()) {
      return mySecondRateResults;
    }
    return ContainerUtil.filter(myResults.values(), element -> !mySecondRateResults.contains(element));
  }

  public void clear() {
    myResults.clear();
    mySecondRateResults.clear();
  }

  @Override
  public boolean shouldProcess(@NotNull DeclarationKind kind) {
    return switch (kind) {
      case CLASS -> myFilter.isClassAcceptable(PsiClass.class);
      case FIELD -> myFilter.isClassAcceptable(PsiField.class);
      case METHOD -> myFilter.isClassAcceptable(PsiMethod.class);
      case PACKAGE -> myFilter.isClassAcceptable(PsiPackage.class);
      case VARIABLE -> myFilter.isClassAcceptable(PsiVariable.class);
      case ENUM_CONST -> myFilter.isClassAcceptable(PsiEnumConstant.class);
    };
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    if (hintKey == JavaCompletionHints.NAME_FILTER) {
      //noinspection unchecked
      return (T)myMatcher;
    }

    return null;
  }

  public static final class Options {
    public static final Options DEFAULT_OPTIONS = new Options(true, true, false, true);
    public static final Options CHECK_NOTHING = new Options(false, false, false, false);
    final boolean checkAccess;
    final boolean filterStaticAfterInstance;
    final boolean showInstanceInStaticContext;
    final boolean instantiableOnly;

    private Options(boolean checkAccess, boolean filterStaticAfterInstance, boolean showInstanceInStaticContext, boolean instantiableOnly) {
      this.checkAccess = checkAccess;
      this.filterStaticAfterInstance = filterStaticAfterInstance;
      this.showInstanceInStaticContext = showInstanceInStaticContext;
      this.instantiableOnly = instantiableOnly;
    }

    public Options withCheckAccess(boolean checkAccess) {
      return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext, instantiableOnly);
    }
    public Options withFilterStaticAfterInstance(boolean filterStaticAfterInstance) {
      return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext, instantiableOnly);
    }
    public Options withShowInstanceInStaticContext(boolean showInstanceInStaticContext) {
      return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext, instantiableOnly);
    }
    public Options withInstantiableOnly(boolean instantiableOnly) {
      return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext, instantiableOnly);
    }
  }

  private enum StaticProblem { none, staticAfterInstance, instanceAfterStatic }
}

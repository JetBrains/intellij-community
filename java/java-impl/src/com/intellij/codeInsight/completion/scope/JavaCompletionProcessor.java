/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstanceBase;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:13:27
 * To change this template use Options | File Templates.
 */
public class JavaCompletionProcessor extends BaseScopeProcessor implements ElementClassHint {

  private final boolean myInJavaDoc;
  private boolean myStatic = false;
  private PsiElement myDeclarationHolder = null;
  private final Map<CompletionElement, CompletionElement> myResults = new LinkedHashMap<>();
  private final Set<CompletionElement> mySecondRateResults = ContainerUtil.newIdentityTroveSet();
  private final Set<String> myShadowedNames = ContainerUtil.newHashSet();
  private final Set<String> myCurrentScopeMethodNames = ContainerUtil.newHashSet();
  private final Set<String> myFinishedScopesMethodNames = ContainerUtil.newHashSet();
  private final Set<PsiMethod> myMethodsToQualify = ContainerUtil.newHashSet();
  private final PsiElement myElement;
  private final PsiElement myScope;
  private final ElementFilter myFilter;
  private boolean myMembersFlag = false;
  private boolean myQualified = false;
  private PsiType myQualifierType = null;
  private PsiClass myQualifierClass = null;
  private final Condition<String> myMatcher;
  private final Options myOptions;
  private final Set<PsiField> myNonInitializedFields = new HashSet<>();
  private final boolean myAllowStaticWithInstanceQualifier;

  public JavaCompletionProcessor(@NotNull PsiElement element, ElementFilter filter, Options options, @NotNull Condition<String> nameCondition) {
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

    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        final PsiJavaCodeReferenceElement qSuper = ((PsiSuperExpression)qualifier).getQualifier();
        if (qSuper == null) {
          myQualifierClass = JavaResolveUtil.getContextClass(myElement);
        } else {
          final PsiElement target = qSuper.resolve();
          myQualifierClass = target instanceof PsiClass ? (PsiClass)target : null;
        }
      }
      else if (qualifier != null) {
        myQualified = true;
        setQualifierType(qualifier.getType());
        if (myQualifierType == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (target instanceof PsiClass) {
            myQualifierClass = (PsiClass)target;
          }
        }
      } else {
        myQualifierClass = JavaResolveUtil.getContextClass(myElement);
      }
    }
    if (myQualifierClass != null && myQualifierType == null) {
      myQualifierType = JavaPsiFacade.getElementFactory(element.getProject()).createType(myQualifierClass);
    }

    if (myOptions.checkInitialized) {
      myNonInitializedFields.addAll(getNonInitializedFields(element));
    }

    myAllowStaticWithInstanceQualifier = !options.filterStaticAfterInstance ||
                                         SuppressManager.getInstance()
                                           .isSuppressedFor(element, AccessStaticViaInstanceBase.ACCESS_STATIC_VIA_INSTANCE);

  }

  private static boolean isInitializedImplicitly(PsiField field) {
    field = CompletionUtil.getOriginalOrSelf(field);
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensions()) {
      if (provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  public static Set<PsiField> getNonInitializedFields(PsiElement element) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    //noinspection SSBasedInspection
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiClass.class);
    if (statement == null || method == null || !method.isConstructor()) {
      return Collections.emptySet();
    }

    PsiElement parent = element.getParent();
    while (parent != statement) {
      PsiElement next = parent.getParent();
      if (next instanceof PsiAssignmentExpression && parent == ((PsiAssignmentExpression)next).getLExpression()) {
        return Collections.emptySet();
      }
      if (parent instanceof PsiReferenceExpression && next instanceof PsiExpressionStatement) {
        return Collections.emptySet();
      }
      parent = next;
    }

    final Set<PsiField> fields = new HashSet<>();
    final PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;
    for (PsiField field : containingClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() == null && !isInitializedImplicitly(field)) {
        fields.add(field);
      }
    }

    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiExpression lExpression = expression.getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            //noinspection SuspiciousMethodCalls
            fields.remove(((PsiReferenceExpression)lExpression).resolve());
          }
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiReferenceExpression methodExpression = expression.getMethodExpression();
          if (methodExpression.textMatches("this")) {
            fields.clear();
          }
        }
        super.visitMethodCallExpression(expression);
      }
    });
    return fields;
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC){
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
    //noinspection SuspiciousMethodCalls
    if (myNonInitializedFields.contains(element)) {
      return true;
    }

    if (element instanceof PsiPackage && !isQualifiedContext()) {
      if (myScope instanceof PsiClass) {
        return true;
      }
      if (((PsiPackage)element).getQualifiedName().contains(".") &&
          PsiTreeUtil.getParentOfType(myElement, PsiImportStatementBase.class) != null) {
        return true;
      }
    }

    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (PsiTypesUtil.isGetClass(method) && PsiUtil.isLanguageLevel5OrHigher(myElement)) {
        PsiType patchedType = PsiTypesUtil.createJavaLangClassType(myElement, myQualifierType, false);
        if (patchedType != null) {
          element = new LightMethodBuilder(element.getManager(), method.getName()).
            addModifier(PsiModifier.PUBLIC).
            setMethodReturnType(patchedType).
            setContainingClass(method.getContainingClass());
        }
      }
    }

    if (element instanceof PsiVariable) {
      String name = ((PsiVariable)element).getName();
      if (myShadowedNames.contains(name)) return true;
      if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
        myShadowedNames.add(name);
      }
    }

    if (!satisfies(element, state) || !isAccessible(element)) return true;

    StaticProblem sp = myElement.getParent() instanceof PsiMethodReferenceExpression ? StaticProblem.none : getStaticProblem(element);
    if (sp == StaticProblem.instanceAfterStatic) return true;

    CompletionElement completion = new CompletionElement(element,  state.get(PsiSubstitutor.KEY));
    CompletionElement prev = myResults.get(completion);
    if (prev == null || completion.isMoreSpecificThan(prev)) {
      myResults.put(completion, completion);
      if (sp == StaticProblem.staticAfterInstance) {
        mySecondRateResults.add(completion);
      }

      if (element instanceof PsiMethod) {
        String name = ((PsiMethod)element).getName();
        myCurrentScopeMethodNames.add(name);
        if (myFinishedScopesMethodNames.contains(name)) {
          myMethodsToQualify.add((PsiMethod)element);
        }
      }
    }

    return true;
  }

  public boolean shouldQualifyMethodCall(@NotNull PsiMethod method) {
    return myMethodsToQualify.contains(method);
  }

  private boolean isQualifiedContext() {
    final PsiElement elementParent = myElement.getParent();
    return elementParent instanceof PsiQualifiedReference && ((PsiQualifiedReference)elementParent).getQualifier() != null;
  }

  private StaticProblem getStaticProblem(PsiElement element) {
    if (myOptions.showInstanceInStaticContext && !isQualifiedContext()) {
      return StaticProblem.none;
    }
    if (element instanceof PsiModifierListOwner) {
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      if (myStatic) {
        if (!(element instanceof PsiClass) && !modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
          // we don't need non static method in static context.
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
    final String name = PsiUtilCore.getName(element);
    if (name != null && StringUtil.isNotEmpty(name) && myMatcher.value(name)) {
      if (myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(new CandidateInfo(element, state.get(PsiSubstitutor.KEY)), myElement)) {
        return true;
      }
    }
    return false;
  }

  public void setQualifierType(@Nullable PsiType qualifierType) {
    myQualifierType = qualifierType;
    myQualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifierType);
  }

  @Nullable
  public PsiType getQualifierType() {
    return myQualifierType;
  }

  public boolean isAccessible(@Nullable final PsiElement element) {
    // if checkAccess is false, we only show inaccessible source elements because their access modifiers can be changed later by the user.
    // compiled element can't be changed so we don't pollute the completion with them. In Javadoc, everything is allowed.
    if (!myOptions.checkAccess && myInJavaDoc) return true;
    if (!(element instanceof PsiMember)) return true;

    PsiMember member = (PsiMember)element;
    PsiClass accessObjectClass = myQualified ? myQualifierClass : null;
    if (JavaPsiFacade.getInstance(element.getProject()).getResolveHelper().isAccessible(member, member.getModifierList(), myElement,
                                                                                        accessObjectClass, myDeclarationHolder)) {
      return true;
    }
    return !myOptions.checkAccess && !(element instanceof PsiCompiledElement);
  }

  public void setCompletionElements(@NotNull Object[] elements) {
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
  public boolean shouldProcess(DeclarationKind kind) {
    switch (kind) {
      case CLASS:
        return myFilter.isClassAcceptable(PsiClass.class);

      case FIELD:
        return myFilter.isClassAcceptable(PsiField.class);

      case METHOD:
        return myFilter.isClassAcceptable(PsiMethod.class);

      case PACKAGE:
        return myFilter.isClassAcceptable(PsiPackage.class);

      case VARIABLE:
        return myFilter.isClassAcceptable(PsiVariable.class);

      case ENUM_CONST:
        return myFilter.isClassAcceptable(PsiEnumConstant.class);
    }

    return false;
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

    return super.getHint(hintKey);
  }

  public static class Options {
    public static final Options DEFAULT_OPTIONS = new Options(true, false, true, false);
    public static final Options CHECK_NOTHING = new Options(false, false, false, false);
    final boolean checkAccess;
    final boolean checkInitialized;
    final boolean filterStaticAfterInstance;
    final boolean showInstanceInStaticContext;

    private Options(boolean checkAccess, boolean checkInitialized, boolean filterStaticAfterInstance, boolean showInstanceInStaticContext) {
      this.checkAccess = checkAccess;
      this.checkInitialized = checkInitialized;
      this.filterStaticAfterInstance = filterStaticAfterInstance;
      this.showInstanceInStaticContext = showInstanceInStaticContext;
    }

    public Options withInitialized(boolean checkInitialized) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
    public Options withCheckAccess(boolean checkAccess) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
    public Options withFilterStaticAfterInstance(boolean filterStaticAfterInstance) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
    public Options withShowInstanceInStaticContext(boolean showInstanceInStaticContext) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
  }
  
  private enum StaticProblem { none, staticAfterInstance, instanceAfterStatic }
}

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
package com.intellij.compiler.classFilesIndex.chainsSearch.context;

import com.intellij.compiler.classFilesIndex.chainsSearch.CachedRelevantStaticMethodSearcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.compiler.classFilesIndex.impl.MethodIncompleteSignature;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ChainCompletionContext {
  private final TargetType myTarget;
  private final Set<String> myContainingClassQNames;
  private final MultiMap<String, PsiVariable> myContextVars;
  private final MultiMap<String, PsiMethod> myContainingClassGetters;
  private final MultiMap<String, ContextRelevantVariableGetter> myContextVarsGetters;
  private final Map<String, PsiVariable> myStringVars;
  private final Set<String> myExcludedQNames;
  private final GlobalSearchScope myResolveScope;
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final MethodIncompleteSignatureResolver myNotDeprecatedMethodsResolver;

  private final NotNullLazyValue<Set<String>> contextTypesQNames = new NotNullLazyValue<Set<String>>() {
    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    protected Set<String> compute() {
      return unionToHashSet(myContainingClassQNames,
                            myContextVars.keySet(),
                            myContainingClassGetters.keySet(),
                            myContextVarsGetters.keySet());
    }
  };

  public Set<String> getExcludedQNames() {
    return myExcludedQNames;
  }

  ChainCompletionContext(final TargetType target,
                         final Set<String> containingClassQNames,
                         final MultiMap<String, PsiVariable> contextVars,
                         final MultiMap<String, PsiMethod> containingClassGetters,
                         final MultiMap<String, ContextRelevantVariableGetter> contextVarsGetters,
                         final Map<String, PsiVariable> stringVars,
                         final Set<String> excludedQNames,
                         final Project project,
                         final GlobalSearchScope resolveScope) {
    myTarget = target;
    myContainingClassQNames = containingClassQNames;
    myContextVars = contextVars;
    myContainingClassGetters = containingClassGetters;
    myContextVarsGetters = contextVarsGetters;
    myStringVars = stringVars;
    myExcludedQNames = excludedQNames;
    myResolveScope = resolveScope;
    myProject = project;
    myPsiManager = PsiManager.getInstance(project);
    myNotDeprecatedMethodsResolver = new MethodIncompleteSignatureResolver(JavaPsiFacade.getInstance(project), resolveScope);
  }

  public TargetType getTarget() {
    return myTarget;
  }

  @Nullable
  public PsiVariable findRelevantStringInContext(@Nullable final String stringParamName) {
    if (stringParamName == null) {
      return null;
    }
    for (final Map.Entry<String, PsiVariable> e : myStringVars.entrySet()) {
      if (ChainCompletionContextStringUtil.isSimilar(e.getKey(), stringParamName)) {
        return e.getValue();
      }
    }
    return null;
  }

  public Set<String> getContainingClassQNames() {
    return myContainingClassQNames;
  }

  public Collection<PsiVariable> getVariables(final String typeQName) {
    return myContextVars.get(typeQName);
  }

  public Collection<PsiMethod> getContainingClassMethods(final String typeQName) {
    return myContainingClassGetters.get(typeQName);
  }

  public Collection<ContextRelevantVariableGetter> getRelevantVariablesGetters(final String typeQName) {
    return myContextVarsGetters.get(typeQName);
  }

  public Collection<?> getContextRefElements(final String typeQName) {
    final Collection<PsiVariable> variables = getVariables(typeQName);
    final Collection<PsiMethod> containingClassMethods = getContainingClassMethods(typeQName);
    final Collection<UserDataHolder> refElements = new ArrayList<>(variables.size() + containingClassMethods.size());
    refElements.addAll(variables);
    refElements.addAll(containingClassMethods);
    for (final ContextRelevantVariableGetter contextRelevantVariableGetter : getRelevantVariablesGetters(typeQName)) {
      refElements.add(contextRelevantVariableGetter.createLookupElement());
    }
    return refElements;
  }

  public boolean contains(@Nullable final String typeQualifierName) {
    return typeQualifierName != null && contextTypesQNames.getValue().contains(typeQualifierName);
  }

  public Set<String> getContextTypes() {
    return contextTypesQNames.getValue();
  }

  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }

  public Project getProject() {
    return myProject;
  }

  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  @NotNull
  public PsiMethod[] resolveNotDeprecated(final MethodIncompleteSignature methodIncompleteSignature) {
    return myNotDeprecatedMethodsResolver.get(methodIncompleteSignature);
  }

  private static <T> HashSet<T> unionToHashSet(final Collection<T>... collections) {
    final HashSet<T> res = new HashSet<>();
    for (final Collection<T> set : collections) {
      res.addAll(set);
    }
    return res;
  }
}
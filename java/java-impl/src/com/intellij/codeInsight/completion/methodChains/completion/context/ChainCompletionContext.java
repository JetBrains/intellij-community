package com.intellij.codeInsight.completion.methodChains.completion.context;

import com.intellij.codeInsight.completion.methodChains.search.CachedRelevantStaticMethodSearcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ChainCompletionContext {
  private final NotNullLazyValue<String> myContextMethodName = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return myContextMethod.getName();
    }
  };
  private final PsiMethod myContextMethod;
  private final String myTargetQName;
  private final Set<String> myContainingClassQNames;
  private final MultiMap<String, PsiVariable> myContextVars;
  private final MultiMap<String, PsiMethod> myContainingClassGetters;
  private final MultiMap<String, ContextRelevantVariableGetter> myContextVarsGetters;
  private final Map<String, PsiVariable> myStringVars;
  private final CachedRelevantStaticMethodSearcher myStaticMethodSearcher;
  private final Set<String> myExcludedQNames;
  private final GlobalSearchScope myResolveScope;
  private final Project myProject;

  private final NotNullLazyValue<Set<String>> contextTypesQNames = new NotNullLazyValue<Set<String>>() {
    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    protected Set<String> compute() {
      return unionToHashSet(myContainingClassQNames, myContextVars.keySet(), myContainingClassGetters.keySet(),
                            myContextVarsGetters.keySet());
    }
  };

  public Set<String> getExcludedQNames() {
    return myExcludedQNames;
  }

  ChainCompletionContext(final PsiMethod contextMethod,
                         final String targetQName,
                         final Set<String> containingClassQNames,
                         final MultiMap<String, PsiVariable> contextVars,
                         final MultiMap<String, PsiMethod> containingClassGetters,
                         final MultiMap<String, ContextRelevantVariableGetter> contextVarsGetters,
                         final Map<String, PsiVariable> stringVars,
                         final Set<String> excludedQNames,
                         final Project project,
                         final GlobalSearchScope resolveScope) {
    myContextMethod = contextMethod;
    myTargetQName = targetQName;
    myContainingClassQNames = containingClassQNames;
    myContextVars = contextVars;
    myContainingClassGetters = containingClassGetters;
    myContextVarsGetters = contextVarsGetters;
    myStringVars = stringVars;
    myExcludedQNames = excludedQNames;
    myResolveScope = resolveScope;
    myProject = project;
    myStaticMethodSearcher = new CachedRelevantStaticMethodSearcher(project, resolveScope);
  }

  public PsiMethod getContextMethod() {
    return myContextMethod;
  }

  public String getContextMethodName() {
    return myContextMethodName.getValue();
  }

  public String getTargetQName() {
    return myTargetQName;
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

  public Collection<ContextRelevantStaticMethod> getRelevantStaticMethods(final String typeQName, final int weight) {
    return myStaticMethodSearcher.getRelevantStaticMethods(typeQName, weight, this);
  }

  public Collection<ContextRelevantVariableGetter> getRelevantVariablesGetters(final String typeQName) {
    return myContextVarsGetters.get(typeQName);
  }

  public Collection<?> getContextRefElements(final String typeQName) {
    final Collection<PsiVariable> variables = getVariables(typeQName);
    final Collection<PsiMethod> containingClassMethods = getContainingClassMethods(typeQName);
    final Collection<UserDataHolder> refElements = new ArrayList<UserDataHolder>(variables.size() + containingClassMethods.size());
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

  private static <T> HashSet<T> unionToHashSet(final Collection<T>... collections) {
    final HashSet<T> res = new HashSet<T>();
    for (final Collection<T> set : collections) {
      res.addAll(set);
    }
    return res;
  }
}

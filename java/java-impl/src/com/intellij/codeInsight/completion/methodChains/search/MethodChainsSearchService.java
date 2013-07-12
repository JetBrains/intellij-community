package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.MethodsUsageIndex;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.compilerOutputIndex.impl.bigram.BigramMethodsUsageIndex;
import com.intellij.compilerOutputIndex.impl.callingLocation.MethodNameAndQualifier;
import com.intellij.codeInsight.completion.methodChains.search.service.OverridenMethodsService;
import com.intellij.codeInsight.completion.methodChains.search.service.SingletonService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodChainsSearchService {
  private final static SortedSet EMPTY_SORTED_SET = new TreeSet();

  private final MethodsUsageIndex myMethodsUsageIndex;
  private final BigramMethodsUsageIndex myBigramMethodsUsageIndex;
  private final SingletonService mySingletonService;
  private final OverridenMethodsService myOverridenMethodsService;
  private final Project myProject;
  private final Map<String, Boolean> mySingletonLocalCache;

  public MethodChainsSearchService(final Project project) {
    myOverridenMethodsService = new OverridenMethodsService(project);
    myMethodsUsageIndex = MethodsUsageIndex.getInstance(project);
    myBigramMethodsUsageIndex = BigramMethodsUsageIndex.getInstance(project);
    mySingletonService = new SingletonService(project);
    myProject = project;

    mySingletonLocalCache = new HashMap<String, Boolean>();
    mySingletonLocalCache.put(null, false);
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public SortedSet<UsageIndexValue> getBigram(final MethodIncompleteSignature methodIncompleteSignature) {
    final TreeSet<UsageIndexValue> value = myBigramMethodsUsageIndex.getValues(methodIncompleteSignature);
    if (value != null) {
      return value;
    }
    return EMPTY_SORTED_SET;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public SortedSet<UsageIndexValue> getMethods(final String targetQName) {
    final TreeSet<UsageIndexValue> value = myMethodsUsageIndex.getValues(targetQName);
    if (value != null) {
      return value;
    }
    return EMPTY_SORTED_SET;
  }

  public boolean isSingleton(@NotNull final PsiClass psiClass, final String contextMethodName) {
    return isSingleton(psiClass.getQualifiedName(), contextMethodName);
  }

  public boolean isSingleton(@Nullable final String typeQName, final String methodName) {
    Boolean isSingleton = mySingletonLocalCache.get(typeQName);
    if (isSingleton == null) {
      isSingleton = mySingletonService.isSingleton(typeQName, methodName);
      mySingletonLocalCache.put(typeQName, isSingleton);
    }
    return isSingleton;
  }

  public boolean isRelevantMethodForField(@NotNull final String className, @NotNull final String methodName) {
    final Pair<Integer, Integer> occurrences =
      myOverridenMethodsService.getMethodUsageInFieldContext(new MethodNameAndQualifier(methodName, className));
    return occurrences.getFirst() > occurrences.getSecond();
  }

  public boolean isRelevantMethodForNotOverriden(@NotNull final String className, @NotNull final String methodName) {
    final Pair<Integer, Integer> occurrences =
      myOverridenMethodsService.getMethodsUsageInOverridenContext(new MethodNameAndQualifier(methodName, className));
    return occurrences.getFirst() < occurrences.getSecond();
  }
}

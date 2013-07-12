package com.intellij.codeInsight.completion.methodChains.search.service;

import com.intellij.codeInsight.completion.methodChains.Constants;
import com.intellij.compilerOutputIndex.impl.singleton.MethodShortSignatureWithWeight;
import com.intellij.compilerOutputIndex.impl.singleton.ParamsInMethodOccurrencesIndex;
import com.intellij.compilerOutputIndex.impl.singleton.TwinVariablesIndex;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class SingletonService {
  private final TwinVariablesIndex myTwinVariablesIndex;
  private final ParamsInMethodOccurrencesIndex myParamsInMethodOccurrencesIndex;
  private final GlobalSearchScope myAllScope;
  private final JavaPsiFacade myJavaPsiFacade;

  private final Map<String, Boolean> myLocalCache;

  public SingletonService(final Project project) {
    myTwinVariablesIndex = TwinVariablesIndex.getInstance(project);
    myParamsInMethodOccurrencesIndex = ParamsInMethodOccurrencesIndex.getInstance(project);
    myAllScope = GlobalSearchScope.allScope(project);
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);

    myLocalCache =  new HashMap<String, Boolean>();
    myLocalCache.put(null, false);
  }

  public boolean isSingleton(@Nullable final String typeQName, final @NotNull String contextMethodName) {
    final Boolean isSingletonCached = myLocalCache.get(typeQName);
    if (isSingletonCached == null) {
      assert typeQName != null;
      final PsiClass aClass = myJavaPsiFacade.findClass(typeQName, myAllScope);
      if (aClass == null) {
        myLocalCache.put(typeQName, false);
        return false;
      }
      for (final PsiClass psiClass : aClass.getInterfaces()) {
        final String qualifiedName = psiClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName) || !isSingleton(qualifiedName, contextMethodName)) {
          myLocalCache.put(typeQName, false);
          return false;
        }
      }
      final boolean isSingleton = hasTwinsFeature(typeQName) && isSuitableTypeFor(typeQName, contextMethodName);
      myLocalCache.put(typeQName, isSingleton);
      return isSingleton;
    }
    return isSingletonCached;
  }

  public boolean hasTwinsFeature(final String typeQName) {
    final List<Integer> twinInfo = myTwinVariablesIndex.getTwinInfo(typeQName);
    if (twinInfo.isEmpty()) {
      return false;
    }
    int ones = 0;
    for (final int i : twinInfo) {
      if (i == 1) {
        ones++;
      }
    }
    return (twinInfo.size() - ones) * Constants.SINGLETON_MAGIC_RATIO < twinInfo.size();
  }

  private boolean isSuitableTypeFor(final String typeName, final String methodName) {
    final Pair<List<MethodShortSignatureWithWeight>, Integer> parameterOccurrences =
      myParamsInMethodOccurrencesIndex.getParameterOccurrences(typeName);
    if (parameterOccurrences.getSecond() == 0) {
      return true;
    }
    final List<MethodShortSignatureWithWeight> contextMethods = parameterOccurrences.getFirst();
    final MethodShortSignatureWithWeight last = contextMethods.get(contextMethods.size() - 1);
    return last.getMethodShortSignature().getName().equals(methodName) || last.getWeight() * Constants.SINGLETON_MAGIC_RATIO2 <= parameterOccurrences.getSecond();
  }

}

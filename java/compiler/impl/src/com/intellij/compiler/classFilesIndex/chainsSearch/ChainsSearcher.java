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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.TargetType;
import com.intellij.compiler.classFilesIndex.impl.MethodIncompleteSignature;
import com.intellij.compiler.classFilesIndex.impl.MethodsUsageIndexReader;
import com.intellij.compiler.classFilesIndex.impl.UsageIndexValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class ChainsSearcher {
  private ChainsSearcher() {
  }

  private static final Logger LOG = Logger.getInstance(ChainsSearcher.class);
  private static final double NEXT_METHOD_IN_CHAIN_RATIO = 1.5;

  @NotNull
  public static List<MethodsChain> search(final int pathMaximalLength,
                                          final TargetType targetType,
                                          final Set<String> contextQNames,
                                          final int maxResultSize,
                                          final ChainCompletionContext context,
                                          final MethodsUsageIndexReader methodsUsageIndexReader) {
    final SearchInitializer initializer = createInitializer(targetType, context.getExcludedQNames(), methodsUsageIndexReader, context);
    if (initializer == null) {
      return Collections.emptyList();
    }
    return search(methodsUsageIndexReader,
                  initializer,
                  contextQNames,
                  pathMaximalLength,
                  maxResultSize,
                  targetType.getClassQName(),
                  context);
  }

  @Nullable
  private static SearchInitializer createInitializer(final TargetType target,
                                                     final Set<String> excludedParamsTypesQNames,
                                                     final MethodsUsageIndexReader methodsUsageIndexReader,
                                                     final ChainCompletionContext context) {
    final SortedSet<UsageIndexValue> methods = methodsUsageIndexReader.getMethods(target.getClassQName());
    return new SearchInitializer(methods, target.getClassQName(), excludedParamsTypesQNames, context);
  }

  @NotNull
  private static List<MethodsChain> search(final MethodsUsageIndexReader indexReader,
                                           final SearchInitializer initializer,
                                           final Set<String> toSet,
                                           final int pathMaximalLength,
                                           final int maxResultSize,
                                           final String targetQName,
                                           final ChainCompletionContext context) {
    final Set<String> allExcludedNames = MethodChainsSearchUtil.joinToHashSet(context.getExcludedQNames(), targetQName);
    final SearchInitializer.InitResult initResult = initializer.init(Collections.<String>emptySet());

    final Map<MethodIncompleteSignature, MethodsChain> knownDistance = initResult.getChains();

    final List<WeightAware<MethodIncompleteSignature>> allInitialVertexes = initResult.getVertexes();

    final LinkedList<WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>> q =
      new LinkedList<>(ContainerUtil.map(allInitialVertexes,
                                         methodIncompleteSignatureWeightAware -> {
                                           final MethodIncompleteSignature underlying =
                                             methodIncompleteSignatureWeightAware.getUnderlying();
                                           return new WeightAware<>(
                                             Pair.create(
                                               underlying,
                                               new MethodsChain(context
                                                                  .resolveNotDeprecated(
                                                                    underlying),
                                                                methodIncompleteSignatureWeightAware
                                                                  .getWeight(),
                                                                underlying
                                                                  .getOwner())),
                                             methodIncompleteSignatureWeightAware
                                               .getWeight()
                                           );
                                         }
      ));

    int maxWeight = 0;
    for (final MethodsChain methodsChain : knownDistance.values()) {
      if (methodsChain.getChainWeight() > maxWeight) {
        maxWeight = methodsChain.getChainWeight();
      }
    }

    final ResultHolder result = new ResultHolder(context.getPsiManager());
    while (!q.isEmpty()) {
      ProgressManager.checkCanceled();
      final WeightAware<Pair<MethodIncompleteSignature, MethodsChain>> currentVertex = q.poll();
      final int currentVertexDistance = currentVertex.getWeight();
      final Pair<MethodIncompleteSignature, MethodsChain> currentVertexUnderlying = currentVertex.getUnderlying();
      final MethodsChain currentVertexMethodsChain = knownDistance.get(currentVertexUnderlying.getFirst());
      if (currentVertexDistance != currentVertexMethodsChain.getChainWeight()) {
        continue;
      }
      if (currentVertex.getUnderlying().getFirst().isStatic() || toSet.contains(currentVertex.getUnderlying().getFirst().getOwner())) {
        result.add(currentVertex.getUnderlying().getSecond());
        continue;
      }
      final String currentReturnType = currentVertexUnderlying.getFirst().getOwner();
      final SortedSet<UsageIndexValue> nextMethods = indexReader.getMethods(currentReturnType);
      final MaxSizeTreeSet<WeightAware<MethodIncompleteSignature>> currentSignatures =
        new MaxSizeTreeSet<>(maxResultSize);
      for (final UsageIndexValue indexValue : nextMethods) {
        final MethodIncompleteSignature vertex = indexValue.getMethodIncompleteSignature();
        final int occurrences = indexValue.getOccurrences();
        if (vertex.isStatic() || !vertex.getOwner().equals(targetQName)) {
          final int vertexDistance = Math.min(currentVertexDistance, occurrences);
          final MethodsChain knownVertexMethodsChain = knownDistance.get(vertex);
          if ((knownVertexMethodsChain == null || knownVertexMethodsChain.getChainWeight() < vertexDistance)) {
            if (currentSignatures.isEmpty() || currentSignatures.last().getWeight() < vertexDistance) {
              if (currentVertexMethodsChain.size() < pathMaximalLength - 1) {
                final MethodIncompleteSignature methodInvocation = indexValue.getMethodIncompleteSignature();
                final PsiMethod[] psiMethods = context.resolveNotDeprecated(methodInvocation);
                if (psiMethods.length != 0 && MethodChainsSearchUtil.checkParametersForTypesQNames(psiMethods, allExcludedNames)) {
                  final MethodsChain newBestMethodsChain =
                    currentVertexMethodsChain.addEdge(psiMethods, indexValue.getMethodIncompleteSignature().getOwner(), vertexDistance);
                  currentSignatures
                    .add(new WeightAware<>(indexValue.getMethodIncompleteSignature(), vertexDistance));
                  knownDistance.put(vertex, newBestMethodsChain);
                }
              }
            }
          }
          else {
            break;
          }
        }
      }
      boolean updated = false;
      if (!currentSignatures.isEmpty()) {
        boolean isBreak = false;
        for (final WeightAware<MethodIncompleteSignature> sign : currentSignatures) {
          final PsiMethod[] resolved = context.resolveNotDeprecated(sign.getUnderlying());
          if (!isBreak) {
            if (sign.getWeight() * NEXT_METHOD_IN_CHAIN_RATIO > currentVertex.getWeight()) {
              final boolean stopChain = sign.getUnderlying().isStatic() || toSet.contains(sign.getUnderlying().getOwner());
              if (stopChain) {
                updated = true;
                result.add(currentVertex.getUnderlying().getSecond().addEdge(resolved, sign.getUnderlying().getOwner(), sign.getWeight()));
                continue;
              }
              else {
                updated = true;
                final MethodsChain methodsChain =
                  currentVertexUnderlying.second.addEdge(resolved, sign.getUnderlying().getOwner(), sign.getWeight());
                q.add(new WeightAware<>(
                  Pair.create(sign.getUnderlying(), methodsChain), sign.getWeight()));
                continue;
              }
            }
          }
          final MethodsChain methodsChain =
            currentVertexUnderlying.second.addEdge(resolved, sign.getUnderlying().getOwner(), sign.getWeight());
          final ParametersMatcher.MatchResult parametersMatchResult = ParametersMatcher.matchParameters(methodsChain, context);
          if (parametersMatchResult.noUnmatchedAndHasMatched() && parametersMatchResult.hasTarget()) {
            updated = true;
            q.addFirst(new WeightAware<>(
              Pair.create(sign.getUnderlying(), methodsChain), sign.getWeight()));
          }
          isBreak = true;
        }
      }
      if (!updated &&
          (currentVertex.getUnderlying().getFirst().isStatic() ||
           !targetQName.equals(currentVertex.getUnderlying().getFirst().getOwner()))) {
        result.add(currentVertex.getUnderlying().getSecond());
      }
      if (result.size() > maxResultSize) {
        return result.getResult();
      }
    }
    return result.getResult();
  }

  private static MethodsChain createChainFromFirstElement(final MethodsChain chain, final PsiClass newQualifierClass) {
    final String qualifiedClassName = newQualifierClass.getQualifiedName();
    if (qualifiedClassName == null) {
      throw new IllegalArgumentException();
    }
    return new MethodsChain(chain.getFirst(), chain.getChainWeight(), qualifiedClassName);
  }

  private static class ResultHolder {
    private final List<MethodsChain> myResult;
    private final PsiManager myContext;

    private ResultHolder(final PsiManager psiManager) {
      myContext = psiManager;
      myResult = new ArrayList<>();
    }

    public void add(final MethodsChain newChain) {
      if (myResult.isEmpty()) {
        myResult.add(newChain);
        return;
      }
      boolean doAdd = true;
      final Stack<Integer> indexesToRemove = new Stack<>();
      for (int i = 0; i < myResult.size(); i++) {
        final MethodsChain chain = myResult.get(i);
        //
        final MethodsChain.CompareResult r = MethodsChain.compare(chain, newChain, myContext);
        switch (r) {
          case LEFT_CONTAINS_RIGHT:
            indexesToRemove.add(i);
            break;
          case RIGHT_CONTAINS_LEFT:
          case EQUAL:
            doAdd = false;
            break;
          case NOT_EQUAL:
            break;
        }
      }
      while (!indexesToRemove.empty()) {
        myResult.remove((int)indexesToRemove.pop());
      }
      if (doAdd) {
        myResult.add(newChain);
      }
    }

    public List<MethodsChain> getRawResult() {
      return myResult;
    }

    public List<MethodsChain> getResult() {
      return findSimilar(reduceChainsSize(myResult, PsiManager.getInstance(myContext.getProject())), myContext);
    }

    public int size() {
      return myResult.size();
    }

    private static List<MethodsChain> reduceChainsSize(final List<MethodsChain> chains, final PsiManager psiManager) {
      return ContainerUtil.map(chains, chain -> {
        final Iterator<PsiMethod[]> chainIterator = chain.iterator();
        if (!chainIterator.hasNext()) {
          LOG.error("empty chain");
          return chain;
        }
        final PsiMethod[] first = chainIterator.next();
        while (chainIterator.hasNext()) {
          final PsiMethod psiMethod = chainIterator.next()[0];
          if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          final PsiClass current = psiMethod.getContainingClass();
          if (current == null) {
            LOG.error("containing class must be not null");
            return chain;
          }
          final PsiMethod[] currentMethods = current.findMethodsByName(first[0].getName(), true);
          if (currentMethods.length != 0) {
            for (final PsiMethod f : first) {
              final PsiMethod[] fSupers = f.findDeepestSuperMethods();
              final PsiMethod fSuper = fSupers.length == 0 ? first[0] : fSupers[0];
              for (final PsiMethod currentMethod : currentMethods) {
                if (psiManager.areElementsEquivalent(currentMethod, fSuper)) {
                  return createChainFromFirstElement(chain, currentMethod.getContainingClass());
                }
                for (final PsiMethod method : currentMethod.findDeepestSuperMethods()) {
                  if (psiManager.areElementsEquivalent(method, fSuper)) {
                    return createChainFromFirstElement(chain, method.getContainingClass());
                  }
                }
              }
            }
          }
        }
        return chain;
      });
    }

    private static List<MethodsChain> findSimilar(final List<MethodsChain> chains, final PsiManager psiManager) {
      final ResultHolder resultHolder = new ResultHolder(psiManager);
      for (final MethodsChain chain : chains) {
        resultHolder.add(chain);
      }
      return resultHolder.getRawResult();
    }
  }
}
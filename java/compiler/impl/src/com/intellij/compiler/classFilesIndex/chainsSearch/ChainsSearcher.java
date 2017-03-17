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

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.TargetType;
import com.intellij.compiler.classFilesIndex.impl.MethodIncompleteSignature;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ChainsSearcher {
  private static final Logger LOG = Logger.getInstance(ChainsSearcher.class);

  private ChainsSearcher() {
  }

  @NotNull
  public static List<MethodsChain> search(final int pathMaximalLength,
                                          final TargetType targetType,
                                          final Set<PsiType> contextQNames,
                                          final int maxResultSize,
                                          final ChainCompletionContext context,
                                          final CompilerReferenceServiceEx compilerReferenceServiceEx) {
    final SearchInitializer initializer = createInitializer(targetType, compilerReferenceServiceEx, context);
    return search(compilerReferenceServiceEx,
                  initializer,
                  contextQNames,
                  pathMaximalLength,
                  maxResultSize,
                  targetType.getClassQName(),
                  context);
  }

  @NotNull
  private static SearchInitializer createInitializer(final TargetType target,
                                                     final CompilerReferenceServiceEx compilerReferenceServiceEx,
                                                     final ChainCompletionContext context) {
    final SortedSet<OccurrencesAware<MethodIncompleteSignature>> methods = compilerReferenceServiceEx.getMethods(target.getClassQName());
    return new SearchInitializer(methods, target.getPsiType(), context);
  }

  @NotNull
  private static List<MethodsChain> search(final CompilerReferenceServiceEx indexReader,
                                           final SearchInitializer initializer,
                                           final Set<PsiType> toSet,
                                           final int pathMaximalLength,
                                           final int maxResultSize,
                                           final String targetQName,
                                           final ChainCompletionContext context) {
    final Set<PsiType> allExcludedNames = Collections.singleton(context.getTarget().getPsiType());
    final SearchInitializer.InitResult initResult = initializer.init(Collections.emptySet());

    final Map<MethodIncompleteSignature, MethodsChain> knownDistance = initResult.getChains();

    final List<OccurrencesAware<MethodIncompleteSignature>> allInitialVertexes = initResult.getVertexes();

    final LinkedList<OccurrencesAware<Pair<MethodIncompleteSignature, MethodsChain>>> q =
      new LinkedList<>(ContainerUtil.map(allInitialVertexes,
                                         methodIncompleteSignatureOccurrencesAware -> {
                                           final MethodIncompleteSignature underlying =
                                             methodIncompleteSignatureOccurrencesAware.getUnderlying();
                                           return new OccurrencesAware<>(
                                             Pair.create(
                                               underlying,
                                               new MethodsChain(context
                                                                  .resolveNotDeprecated(
                                                                    underlying),
                                                                methodIncompleteSignatureOccurrencesAware
                                                                  .getOccurrences(),
                                                                underlying
                                                                  .getOwner())),
                                             methodIncompleteSignatureOccurrencesAware
                                               .getOccurrences()
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
      final OccurrencesAware<Pair<MethodIncompleteSignature, MethodsChain>> currentVertex = q.poll();
      final int currentVertexDistance = currentVertex.getOccurrences();
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
      final SortedSet<OccurrencesAware<MethodIncompleteSignature>> nextMethods = indexReader.getMethods(currentReturnType);
      final MaxSizeTreeSet<OccurrencesAware<MethodIncompleteSignature>> currentSignatures =
        new MaxSizeTreeSet<>(maxResultSize);
      for (final OccurrencesAware<MethodIncompleteSignature> indexValue : nextMethods) {
        final MethodIncompleteSignature vertex = indexValue.getUnderlying();
        final int occurrences = indexValue.getOccurrences();
        if (vertex.isStatic() || !vertex.getOwner().equals(targetQName)) {
          final int vertexDistance = Math.min(currentVertexDistance, occurrences);
          final MethodsChain knownVertexMethodsChain = knownDistance.get(vertex);
          if ((knownVertexMethodsChain == null || knownVertexMethodsChain.getChainWeight() < vertexDistance)) {
            if (currentSignatures.isEmpty() || currentSignatures.last().getOccurrences() < vertexDistance) {
              if (currentVertexMethodsChain.size() < pathMaximalLength - 1) {
                final MethodIncompleteSignature methodInvocation = indexValue.getUnderlying();
                final PsiMethod[] psiMethods = context.resolveNotDeprecated(methodInvocation);
                if (psiMethods.length != 0 && !MethodChainsSearchUtil.doesMethodsContainParameters(psiMethods, allExcludedNames)) {
                  final MethodsChain newBestMethodsChain =
                    currentVertexMethodsChain.addEdge(psiMethods, indexValue.getUnderlying().getOwner(), vertexDistance);
                  currentSignatures
                    .add(new OccurrencesAware<>(indexValue.getUnderlying(), vertexDistance));
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
        for (final OccurrencesAware<MethodIncompleteSignature> sign : currentSignatures) {
          final PsiMethod[] resolved = context.resolveNotDeprecated(sign.getUnderlying());
          if (!isBreak) {
            if (indexReader.getCoupleOccurrences(sign.getUnderlying().getRef(), currentVertex.getUnderlying().getFirst().getRef())) {
              final boolean stopChain = sign.getUnderlying().isStatic() || toSet.contains(sign.getUnderlying().getOwner());
              if (stopChain) {
                updated = true;
                result.add(currentVertex.getUnderlying().getSecond().addEdge(resolved, sign.getUnderlying().getOwner(), sign.getOccurrences()));
                continue;
              }
              else {
                updated = true;
                final MethodsChain methodsChain =
                  currentVertexUnderlying.second.addEdge(resolved, sign.getUnderlying().getOwner(), sign.getOccurrences());
                q.addFirst(new OccurrencesAware<>(
                  Pair.create(sign.getUnderlying(), methodsChain), sign.getOccurrences()));
                continue;
              }
            }
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
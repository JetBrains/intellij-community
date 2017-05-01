/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch;

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.backwardRefs.MethodIncompleteSignature;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.chainsSearch.context.ChainSearchTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
public class ChainsSearcher {
  private static final Logger LOG = Logger.getInstance(ChainsSearcher.class);

  private ChainsSearcher() {
  }

  @NotNull
  public static List<MethodsChain> search(int pathMaximalLength,
                                          ChainSearchTarget searchTarget,
                                          int maxResultSize,
                                          ChainCompletionContext context,
                                          CompilerReferenceServiceEx compilerReferenceServiceEx) {
    SearchInitializer initializer = createInitializer(searchTarget, compilerReferenceServiceEx, context);
    return search(compilerReferenceServiceEx,
                  initializer,
                  pathMaximalLength,
                  maxResultSize,
                  context);
  }

  @NotNull
  private static SearchInitializer createInitializer(ChainSearchTarget target,
                                                     CompilerReferenceServiceEx compilerReferenceServiceEx,
                                                     ChainCompletionContext context) {
    SortedSet<OccurrencesAware<MethodIncompleteSignature>> methods = null;
    for (byte kind : target.getArrayKind()) {
      SortedSet<OccurrencesAware<MethodIncompleteSignature>> currentMethods =
        compilerReferenceServiceEx.findMethodReferenceOccurrences(target.getClassQName(), kind);
      if (methods == null) {
        methods = currentMethods;
      } else {
        methods.addAll(currentMethods);
      }
    }
    return new SearchInitializer(methods, context);
  }

  @NotNull
  private static List<MethodsChain> search(CompilerReferenceServiceEx indexReader,
                                           SearchInitializer initializer,
                                           int pathMaximalLength,
                                           int maxResultSize,
                                           ChainCompletionContext context) {
    SearchInitializer.InitResult initResult = initializer.init(Collections.emptySet());

    Map<MethodIncompleteSignature, MethodsChain> knownDistance = initResult.getChains();

    LinkedList<OccurrencesAware<MethodsChain>> q = initResult
      .getVertices()
      .stream()
      .map(
        signAndWeight -> new OccurrencesAware<>(MethodsChain.create(signAndWeight.getUnderlying(), signAndWeight.getOccurrences(), context),
                                                signAndWeight.getOccurrences()))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedList::new));

    ResultHolder result = new ResultHolder();
    while (!q.isEmpty()) {
      ProgressManager.checkCanceled();
      OccurrencesAware<MethodsChain> currentVertex = q.poll();
      int currentVertexDistance = currentVertex.getOccurrences();
      MethodsChain currentChain = currentVertex.getUnderlying();
      MethodIncompleteSignature headSignature = currentChain.getHeadSignature();
      MethodsChain currentVertexMethodsChain = knownDistance.get(headSignature);
      if (currentVertexDistance != currentVertexMethodsChain.getChainWeight()) {
        continue;
      }
      if (headSignature.isStatic() || context.hasQualifier(context.resolveQualifierClass(headSignature))) {
        result.add(currentChain);
        continue;
      }
      String currentReturnType = headSignature.getOwner();
      SortedSet<OccurrencesAware<MethodIncompleteSignature>> nextMethods = indexReader.findMethodReferenceOccurrences(currentReturnType, SignatureData.ZERO_DIM);
      MaxSizeTreeSet<OccurrencesAware<MethodIncompleteSignature>> currentSignatures =
        new MaxSizeTreeSet<>(maxResultSize);
      String targetQName = context.getTarget().getClassQName();
      for (OccurrencesAware<MethodIncompleteSignature> indexValue : nextMethods) {
        MethodIncompleteSignature vertex = indexValue.getUnderlying();
        int occurrences = indexValue.getOccurrences();
        if (vertex.isStatic() || !vertex.getOwner().equals(targetQName)) {
          int vertexDistance = Math.min(currentVertexDistance, occurrences);
          MethodsChain knownVertexMethodsChain = knownDistance.get(vertex);
          if ((knownVertexMethodsChain == null || knownVertexMethodsChain.getChainWeight() < vertexDistance)) {
            if (currentSignatures.isEmpty() || currentSignatures.last().getOccurrences() < vertexDistance) {
              if (currentVertexMethodsChain.size() < pathMaximalLength - 1) {
                MethodsChain newBestMethodsChain =
                  currentVertexMethodsChain.continuation(indexValue.getUnderlying(), vertexDistance, context);
                if (newBestMethodsChain != null) {
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
        for (OccurrencesAware<MethodIncompleteSignature> sign : currentSignatures) {
          if (!isBreak) {
            if (indexReader.mayHappen(sign.getUnderlying().getRef(), headSignature.getRef(), ChainSearchMagicConstants.PROBABILITY_THRESHOLD)) {
              boolean stopChain = sign.getUnderlying().isStatic() || context.hasQualifier(context.resolveQualifierClass(sign.getUnderlying()));
              if (stopChain) {
                updated = true;
                MethodsChain continuation = currentChain.continuation(sign.getUnderlying(), sign.getOccurrences(), context);
                if (continuation != null) {
                  result.add(continuation);
                }
                continue;
              }
              else {
                updated = true;
                MethodsChain methodsChain =
                  currentChain.continuation(sign.getUnderlying(), sign.getOccurrences(), context);
                if (methodsChain != null) {
                  q.addFirst(new OccurrencesAware<>(methodsChain, sign.getOccurrences()));
                  continue;
                }
              }
            }
          }
          isBreak = true;
        }
      }
      if (!updated &&
          (headSignature.isStatic() ||
           !targetQName.equals(headSignature.getOwner()))) {
        result.add(currentVertex.getUnderlying());
      }
      if (result.size() > maxResultSize) {
        return result.getResult();
      }
    }
    return result.getResult();
  }

  private static MethodsChain createChainFromFirstElement(MethodsChain chain, PsiClass newQualifierClass) {
    //TODO
    return new MethodsChain(newQualifierClass, Collections.singletonList(chain.getFirst()), null, chain.getChainWeight());
  }

  private static class ResultHolder {
    private final List<MethodsChain> myResult;

    private ResultHolder() {
      myResult = new ArrayList<>();
    }

    public void add(MethodsChain newChain) {
      if (myResult.isEmpty()) {
        myResult.add(newChain);
        return;
      }
      boolean doAdd = true;
      Stack<Integer> indexesToRemove = new Stack<>();
      for (int i = 0; i < myResult.size(); i++) {
        MethodsChain chain = myResult.get(i);
        //
        MethodsChain.CompareResult r = MethodsChain.compare(chain, newChain);
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
      return findSimilar(reduceChainsSize(myResult));
    }

    public int size() {
      return myResult.size();
    }

    private static List<MethodsChain> reduceChainsSize(List<MethodsChain> chains) {
      return ContainerUtil.map(chains, chain -> {
        Iterator<PsiMethod[]> chainIterator = chain.iterator();
        if (!chainIterator.hasNext()) {
          LOG.error("empty chain");
          return chain;
        }
        PsiMethod[] first = chainIterator.next();
        while (chainIterator.hasNext()) {
          PsiMethod psiMethod = chainIterator.next()[0];
          if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          PsiClass current = psiMethod.getContainingClass();
          if (current == null) {
            LOG.error("containing class must be not null");
            return chain;
          }
          PsiMethod[] currentMethods = current.findMethodsByName(first[0].getName(), true);
          if (currentMethods.length != 0) {
            for (PsiMethod f : first) {
              PsiMethod[] fSupers = f.findDeepestSuperMethods();
              PsiMethod fSuper = fSupers.length == 0 ? first[0] : fSupers[0];
              for (PsiMethod currentMethod : currentMethods) {
                if (currentMethod == fSuper) {
                  return createChainFromFirstElement(chain, currentMethod.getContainingClass());
                }
                for (PsiMethod method : currentMethod.findDeepestSuperMethods()) {
                  if (method == fSuper) {
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

    private static List<MethodsChain> findSimilar(List<MethodsChain> chains) {
      ResultHolder resultHolder = new ResultHolder();
      for (MethodsChain chain : chains) {
        resultHolder.add(chain);
      }
      return resultHolder.getRawResult();
    }
  }
}
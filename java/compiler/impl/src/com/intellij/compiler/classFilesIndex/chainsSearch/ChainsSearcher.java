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
  public static List<MethodsChain> search(int pathMaximalLength,
                                          TargetType targetType,
                                          int maxResultSize,
                                          ChainCompletionContext context,
                                          CompilerReferenceServiceEx compilerReferenceServiceEx) {
    SearchInitializer initializer = createInitializer(targetType, compilerReferenceServiceEx, context);
    return search(compilerReferenceServiceEx,
                  initializer,
                  pathMaximalLength,
                  maxResultSize,
                  targetType.getClassQName(),
                  context);
  }

  @NotNull
  private static SearchInitializer createInitializer(TargetType target,
                                                     CompilerReferenceServiceEx compilerReferenceServiceEx,
                                                     ChainCompletionContext context) {
    SortedSet<OccurrencesAware<MethodIncompleteSignature>> methods = compilerReferenceServiceEx.getMethods(target.getClassQName());
    return new SearchInitializer(methods, target.getPsiType(), context);
  }

  @NotNull
  private static List<MethodsChain> search(CompilerReferenceServiceEx indexReader,
                                           SearchInitializer initializer,
                                           int pathMaximalLength,
                                           int maxResultSize,
                                           String targetQName,
                                           ChainCompletionContext context) {
    Set<PsiType> allExcludedNames = Collections.singleton(context.getTarget().getPsiType());
    SearchInitializer.InitResult initResult = initializer.init(Collections.emptySet());

    Map<MethodIncompleteSignature, MethodsChain> knownDistance = initResult.getChains();

    List<OccurrencesAware<MethodIncompleteSignature>> allInitialVertexes = initResult.getVertexes();

    LinkedList<OccurrencesAware<Pair<MethodIncompleteSignature, MethodsChain>>> q =
      new LinkedList<>(ContainerUtil.map(allInitialVertexes,
                                         methodIncompleteSignatureOccurrencesAware -> {
                                           MethodIncompleteSignature underlying =
                                             methodIncompleteSignatureOccurrencesAware.getUnderlying();
                                           return new OccurrencesAware<>(
                                             Pair.create(
                                               underlying,
                                               new MethodsChain(context.resolveQualifierClass(underlying),
                                                                context.resolve(underlying),
                                                                methodIncompleteSignatureOccurrencesAware.getOccurrences())),
                                             methodIncompleteSignatureOccurrencesAware
                                               .getOccurrences()
                                           );
                                         }
      ));

    int maxWeight = 0;
    for (MethodsChain methodsChain : knownDistance.values()) {
      if (methodsChain.getChainWeight() > maxWeight) {
        maxWeight = methodsChain.getChainWeight();
      }
    }

    ResultHolder result = new ResultHolder(context.getPsiManager());
    while (!q.isEmpty()) {
      ProgressManager.checkCanceled();
      OccurrencesAware<Pair<MethodIncompleteSignature, MethodsChain>> currentVertex = q.poll();
      int currentVertexDistance = currentVertex.getOccurrences();
      Pair<MethodIncompleteSignature, MethodsChain> currentVertexUnderlying = currentVertex.getUnderlying();
      MethodsChain currentVertexMethodsChain = knownDistance.get(currentVertexUnderlying.getFirst());
      if (currentVertexDistance != currentVertexMethodsChain.getChainWeight()) {
        continue;
      }
      if (currentVertex.getUnderlying().getFirst().isStatic() || context.hasQualifier(context.resolveQualifierClass(currentVertex.getUnderlying().getFirst()))) {
        result.add(currentVertex.getUnderlying().getSecond());
        continue;
      }
      String currentReturnType = currentVertexUnderlying.getFirst().getOwner();
      SortedSet<OccurrencesAware<MethodIncompleteSignature>> nextMethods = indexReader.getMethods(currentReturnType);
      MaxSizeTreeSet<OccurrencesAware<MethodIncompleteSignature>> currentSignatures =
        new MaxSizeTreeSet<>(maxResultSize);
      for (OccurrencesAware<MethodIncompleteSignature> indexValue : nextMethods) {
        MethodIncompleteSignature vertex = indexValue.getUnderlying();
        int occurrences = indexValue.getOccurrences();
        if (vertex.isStatic() || !vertex.getOwner().equals(targetQName)) {
          int vertexDistance = Math.min(currentVertexDistance, occurrences);
          MethodsChain knownVertexMethodsChain = knownDistance.get(vertex);
          if ((knownVertexMethodsChain == null || knownVertexMethodsChain.getChainWeight() < vertexDistance)) {
            if (currentSignatures.isEmpty() || currentSignatures.last().getOccurrences() < vertexDistance) {
              if (currentVertexMethodsChain.size() < pathMaximalLength - 1) {
                MethodIncompleteSignature methodInvocation = indexValue.getUnderlying();
                PsiMethod[] psiMethods = context.resolve(methodInvocation);
                if (psiMethods.length != 0 && !MethodChainsSearchUtil.doesMethodsContainParameters(psiMethods, allExcludedNames)) {
                  MethodsChain newBestMethodsChain =
                    currentVertexMethodsChain.addEdge(psiMethods, context.resolveQualifierClass(indexValue.getUnderlying()), vertexDistance);
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
          PsiMethod[] resolved = context.resolve(sign.getUnderlying());
          if (!isBreak) {
            if (indexReader.getCoupleOccurrences(sign.getUnderlying().getRef(), currentVertex.getUnderlying().getFirst().getRef())) {
              boolean stopChain = sign.getUnderlying().isStatic() || context.hasQualifier(context.resolveQualifierClass(sign.getUnderlying()));
              if (stopChain) {
                updated = true;
                result.add(currentVertex.getUnderlying().getSecond().addEdge(resolved, context.resolveQualifierClass(sign.getUnderlying()), sign.getOccurrences()));
                continue;
              }
              else {
                updated = true;
                MethodsChain methodsChain =
                  currentVertexUnderlying.second.addEdge(resolved, context.resolveQualifierClass(sign.getUnderlying()), sign.getOccurrences());
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

  private static MethodsChain createChainFromFirstElement(MethodsChain chain, PsiClass newQualifierClass) {
    return new MethodsChain(newQualifierClass, chain.getFirst(), chain.getChainWeight());
  }

  private static class ResultHolder {
    private final List<MethodsChain> myResult;
    private final PsiManager myContext;

    private ResultHolder(PsiManager psiManager) {
      myContext = psiManager;
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
        MethodsChain.CompareResult r = MethodsChain.compare(chain, newChain, myContext);
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

    private static List<MethodsChain> reduceChainsSize(List<MethodsChain> chains, PsiManager psiManager) {
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
                if (psiManager.areElementsEquivalent(currentMethod, fSuper)) {
                  return createChainFromFirstElement(chain, currentMethod.getContainingClass());
                }
                for (PsiMethod method : currentMethod.findDeepestSuperMethods()) {
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

    private static List<MethodsChain> findSimilar(List<MethodsChain> chains, PsiManager psiManager) {
      ResultHolder resultHolder = new ResultHolder(psiManager);
      for (MethodsChain chain : chains) {
        resultHolder.add(chain);
      }
      return resultHolder.getRawResult();
    }
  }
}
package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.codeInsight.completion.methodChains.completion.context.ChainCompletionContext;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class ChainsSearcher {
  private ChainsSearcher() {
  }

  private static final Logger LOG = Logger.getInstance(ChainsSearcher.class);
  private static final double NEXT_METHOD_IN_CHAIN_RATIO = 1.5;

  public static List<MethodsChain> search(final MethodChainsSearchService searchService,
                                          final String targetQName,
                                          final Set<String> contextQNames,
                                          final int maxResultSize,
                                          final int pathMaximalLength,
                                          final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                                          final Set<String> excludedParamsTypesQNames,
                                          final ChainCompletionContext context) {
    final SearchInitializer initializer = createInitializer(targetQName, resolver, searchService, excludedParamsTypesQNames);
    return search(searchService, initializer, contextQNames, pathMaximalLength, maxResultSize, resolver, targetQName,
                  excludedParamsTypesQNames, context);
  }

  private static SearchInitializer createInitializer(final String targetQName,
                                                     final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                                                     final MethodChainsSearchService searchService,
                                                     final Set<String> excludedParamsTypesQNames) {
    return new SearchInitializer(searchService.getMethods(targetQName), resolver, targetQName, excludedParamsTypesQNames);
  }

  @NotNull
  private static List<MethodsChain> search(final MethodChainsSearchService searchService,
                                           final SearchInitializer initializer,
                                           final Set<String> toSet,
                                           final int pathMaximalLength,
                                           final int maxResultSize,
                                           final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                                           final String targetQName,
                                           final Set<String> excludedParamsTypesQNames,
                                           final ChainCompletionContext context) {
    final Set<String> allExcludedNames = MethodChainsSearchUtil.unionToHashSet(excludedParamsTypesQNames, targetQName);
    final SearchInitializer.InitResult initResult = initializer.init(Collections.<String>emptySet());

    final Map<MethodIncompleteSignature, MethodsChain> knownDistance = initResult.getChains();

    final List<WeightAware<MethodIncompleteSignature>> allInitialVertexes = initResult.getVertexes();

    final LinkedList<WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>> q =
      new LinkedList<WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>>(ContainerUtil.map(allInitialVertexes,
                                                                                                   new Function<WeightAware<MethodIncompleteSignature>, WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>>() {
                                                                                                     @Override
                                                                                                     public WeightAware<Pair<MethodIncompleteSignature, MethodsChain>> fun(
                                                                                                       final WeightAware<MethodIncompleteSignature> methodIncompleteSignatureWeightAware) {
                                                                                                       return new WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>(
                                                                                                         new Pair<MethodIncompleteSignature, MethodsChain>(
                                                                                                           methodIncompleteSignatureWeightAware
                                                                                                             .getUnderlying(),
                                                                                                           new MethodsChain(resolver.get(
                                                                                                             methodIncompleteSignatureWeightAware
                                                                                                               .getUnderlying()),
                                                                                                                            methodIncompleteSignatureWeightAware
                                                                                                                              .getWeight(),
                                                                                                                            methodIncompleteSignatureWeightAware
                                                                                                                              .getUnderlying()
                                                                                                                              .getOwner())),
                                                                                                         methodIncompleteSignatureWeightAware
                                                                                                           .getWeight());
                                                                                                     }
                                                                                                   }));

    int maxWeight = 0;
    for (final MethodsChain methodsChain : knownDistance.values()) {
      if (methodsChain.getChainWeight() > maxWeight) {
        maxWeight = methodsChain.getChainWeight();
      }
    }

    final ResultHolder result = new ResultHolder(context);
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
      final SortedSet<UsageIndexValue> nextMethods = searchService.getMethods(currentVertexUnderlying.getFirst().getOwner());
      final MaxSizeTreeSet<WeightAware<MethodIncompleteSignature>> currentSignatures =
        new MaxSizeTreeSet<WeightAware<MethodIncompleteSignature>>(maxResultSize);
      for (final UsageIndexValue indexValue : nextMethods) {
        final MethodIncompleteSignature vertex = indexValue.getMethodIncompleteSignature();
        final int occurrences = indexValue.getOccurrences();
        if (vertex.isStatic() || !vertex.getOwner().equals(targetQName)) {
          final int vertexDistance = Math.min(currentVertexDistance, occurrences);
          final MethodsChain knownVertexMethodsChain = knownDistance.get(vertex);
          if ((knownVertexMethodsChain == null || knownVertexMethodsChain.getChainWeight() < vertexDistance)) {
            if (currentSignatures.isEmpty() || currentSignatures.last().getWeight() < vertexDistance) {
              final MethodIncompleteSignature methodInvocation = indexValue.getMethodIncompleteSignature();
              final PsiMethod[] psiMethods = resolver.get(methodInvocation);
              if (psiMethods.length != 0 && MethodChainsSearchUtil.checkParametersForTypesQNames(psiMethods, allExcludedNames)) {
                final MethodsChain newBestMethodsChain =
                  currentVertexMethodsChain.addEdge(psiMethods, indexValue.getMethodIncompleteSignature().getOwner(), vertexDistance);
                if (newBestMethodsChain.size() <= pathMaximalLength - 1) {
                  currentSignatures
                    .add(new WeightAware<MethodIncompleteSignature>(indexValue.getMethodIncompleteSignature(), vertexDistance));
                }
                knownDistance.put(vertex, newBestMethodsChain);
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
          final PsiMethod[] resolved = resolver.get(sign.getUnderlying());
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
                q.add(new WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>(
                  new Pair<MethodIncompleteSignature, MethodsChain>(sign.getUnderlying(), methodsChain), sign.getWeight()));
                continue;
              }
            }
          }
          final MethodsChain methodsChain =
            currentVertexUnderlying.second.addEdge(resolved, sign.getUnderlying().getOwner(), sign.getWeight());
          final ParametersMatcher.MatchResult parametersMatchResult = ParametersMatcher.matchParameters(methodsChain, context);
          if (parametersMatchResult.noUnmatchedAndHasMatched() && parametersMatchResult.hasTarget()) {
            updated = true;
            q.addFirst(new WeightAware<Pair<MethodIncompleteSignature, MethodsChain>>(
              new Pair<MethodIncompleteSignature, MethodsChain>(sign.getUnderlying(), methodsChain), sign.getWeight()));
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
    private final ChainCompletionContext myContext;

    private ResultHolder(final ChainCompletionContext context) {
      myContext = context;
      myResult = new ArrayList<MethodsChain>();
    }

    public void add(final MethodsChain newChain) {
      if (myResult.isEmpty()) {
        myResult.add(newChain);
        return;
      }
      boolean doAdd = true;
      final Stack<Integer> indexesToRemove = new Stack<Integer>();
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
      return ContainerUtil.map(chains, new Function<MethodsChain, MethodsChain>() {
        @Override
        public MethodsChain fun(final MethodsChain chain) {
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
        }
      });
    }

    private static List<MethodsChain> findSimilar(final List<MethodsChain> chains, final ChainCompletionContext context) {
      final ResultHolder resultHolder = new ResultHolder(context);
      for (final MethodsChain chain : chains) {
        resultHolder.add(chain);
      }
      return resultHolder.getRawResult();
    }
  }
}

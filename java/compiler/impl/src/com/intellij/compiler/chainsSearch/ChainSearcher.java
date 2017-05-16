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
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.chainsSearch.context.ChainSearchTarget;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.IntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.*;

public class ChainSearcher {
  @NotNull
  public static List<MethodChain> search(int pathMaximalLength,
                                         ChainSearchTarget searchTarget,
                                         int maxResultSize,
                                         ChainCompletionContext context,
                                         CompilerReferenceServiceEx compilerReferenceServiceEx) {
    SearchInitializer initializer = createInitializer(searchTarget, compilerReferenceServiceEx, context);
    return search(compilerReferenceServiceEx, initializer, pathMaximalLength, maxResultSize, context);
  }

  @NotNull
  private static SearchInitializer createInitializer(ChainSearchTarget target,
                                                     CompilerReferenceServiceEx referenceServiceEx,
                                                     ChainCompletionContext context) {
    SortedSet<SignatureAndOccurrences> methods = null;
    for (byte kind : target.getArrayKind()) {
      SortedSet<SignatureAndOccurrences> currentMethods =
        referenceServiceEx.findMethodReferenceOccurrences(target.getClassQName(), kind);
      if (methods == null) {
        methods = currentMethods;
      } else {
        methods.addAll(currentMethods);
      }
    }
    return new SearchInitializer(methods, context);
  }

  @NotNull
  private static List<MethodChain> search(CompilerReferenceServiceEx referenceServiceEx,
                                          SearchInitializer initializer,
                                          int pathMaximalLength,
                                          int maxResultSize,
                                          ChainCompletionContext context) {
    Map<MethodIncompleteSignature, MethodChain> knownDistance = initializer.getChains();
    LinkedList<MethodChain> q = initializer.getChainQueue();

    List<MethodChain> result = new ArrayList<>();
    while (!q.isEmpty()) {

      ProgressManager.checkCanceled();
      MethodChain currentVertex = q.poll();
      MethodIncompleteSignature headSignature = currentVertex.getHeadSignature();
      MethodChain currentVertexMethodChain = knownDistance.get(headSignature);
      if (currentVertex.getChainWeight() != currentVertexMethodChain.getChainWeight()) {
        continue;
      }

      // interrupt a chain if a head method is static or has suitable qualifier
      if (headSignature.isStatic() || context.hasQualifier(context.resolveQualifierClass(headSignature))) {
        addChainIfNotPresent(currentVertex, result);
        continue;
      }

      // otherwise try to find chain continuation
      SortedSet<SignatureAndOccurrences> candidates = referenceServiceEx.findMethodReferenceOccurrences(headSignature.getOwner(), SignatureData.ZERO_DIM);
      MaxSizeTreeSet<SignatureAndOccurrences> chosenCandidates = new MaxSizeTreeSet<>(maxResultSize);
      for (SignatureAndOccurrences candidate : candidates) {
        if (candidate.getOccurrenceCount() * ChainSearchMagicConstants.FILTER_RATIO < currentVertex.getChainWeight()) {
          break;
        }
        MethodIncompleteSignature sign = candidate.getSignature();
        if (sign.isStatic() || !sign.getOwner().equals(context.getTarget().getClassQName())) {
          int vertexDistance = Math.min(currentVertex.getChainWeight(), candidate.getOccurrenceCount());
          MethodChain knownVertexMethodChain = knownDistance.get(sign);
          if ((knownVertexMethodChain == null || knownVertexMethodChain.getChainWeight() < vertexDistance)) {
            if ((chosenCandidates.isEmpty() || chosenCandidates.last().getOccurrenceCount() < vertexDistance) && currentVertexMethodChain.size() < pathMaximalLength - 1) {
              MethodChain newBestMethodChain = currentVertexMethodChain.continuation(candidate.getSignature(), vertexDistance, context);
              if (newBestMethodChain != null) {
                chosenCandidates.add(new SignatureAndOccurrences(candidate.getSignature(), vertexDistance));
                knownDistance.put(sign, newBestMethodChain);
              }
            }
          }
          else {
            break;
          }
        }
      }

      boolean updated = false;
      if (!chosenCandidates.isEmpty()) {
        for (SignatureAndOccurrences candidate : chosenCandidates) {
          if (referenceServiceEx.mayHappen(candidate.getSignature().getRef(), headSignature.getRef(), ChainSearchMagicConstants.PROBABILITY_THRESHOLD)) {
            MethodChain continuation = currentVertex.continuation(candidate.getSignature(), candidate.getOccurrenceCount(), context);
            if (continuation != null) {
              boolean stopChain = candidate.getSignature().isStatic() || context.hasQualifier(context.resolveQualifierClass(candidate.getSignature()));
              if (stopChain) {
                addChainIfNotPresent(continuation, result);
              }
              else {
                q.addFirst(continuation);
              }
            }
            updated = true;
          }
        }
      }

      // continuation is not found -> add this chain as result
      if (!updated && !context.getTarget().getClassQName().equals(headSignature.getOwner())) {
        addChainIfNotPresent(currentVertex, result);
      }

      if (result.size() > maxResultSize) {
        return result;
      }
    }
    return result;
  }

  private static void addChainIfNotPresent(MethodChain newChain, List<MethodChain> result) {
    if (result.isEmpty()) {
      result.add(newChain);
      return;
    }
    boolean doAdd = true;
    IntStack indicesToRemove = new IntStack();
    for (int i = 0; i < result.size(); i++) {
      MethodChain chain = result.get(i);
      MethodChain.CompareResult r = MethodChain.compare(chain, newChain);
      switch (r) {
        case LEFT_CONTAINS_RIGHT:
          indicesToRemove.push(i);
          break;
        case RIGHT_CONTAINS_LEFT:
        case EQUAL:
          doAdd = false;
          break;
        case NOT_EQUAL:
          break;
      }
    }
    while (!indicesToRemove.empty()) {
      result.remove(indicesToRemove.pop());
    }
    if (doAdd) {
      result.add(newChain);
    }
  }
}
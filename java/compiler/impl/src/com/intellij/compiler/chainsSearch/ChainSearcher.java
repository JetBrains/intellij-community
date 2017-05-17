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
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;

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
    LinkedList<MethodChain> q = initializer.getChainQueue();

    List<MethodChain> result = new ArrayList<>();
    while (!q.isEmpty()) {

      ProgressManager.checkCanceled();
      MethodChain currentChain = q.poll();
      MethodIncompleteSignature headSignature = currentChain.getHeadSignature();

      // interrupt a chain if it can be terminal
      if (headSignature.isStatic() ||
          context.hasQualifier(context.resolveQualifierClass(headSignature)) ||
          currentChain.length() >= pathMaximalLength) {
        addChainIfNotPresent(currentChain, result);
        continue;
      }

      // otherwise try to find chain continuation
      boolean updated = false;
      SortedSet<SignatureAndOccurrences> candidates = referenceServiceEx.findMethodReferenceOccurrences(headSignature.getOwner(), SignatureData.ZERO_DIM);
      for (SignatureAndOccurrences candidate : candidates) {
        if (candidate.getOccurrenceCount() * ChainSearchMagicConstants.FILTER_RATIO < currentChain.getChainWeight()) {
          break;
        }
        MethodIncompleteSignature sign = candidate.getSignature();
        if ((sign.isStatic() || !sign.getOwner().equals(context.getTarget().getClassQName())) &&
            referenceServiceEx.mayHappen(candidate.getSignature().getRef(), headSignature.getRef(), ChainSearchMagicConstants.METHOD_PROBABILITY_THRESHOLD)) {
          MethodChain continuation = currentChain.continuation(candidate.getSignature(), candidate.getOccurrenceCount(), context);
          if (continuation != null) {
            boolean stopChain = candidate.getSignature().isStatic() || context.hasQualifier(context.resolveQualifierClass(candidate.getSignature()));
            if (stopChain) {
              addChainIfNotPresent(continuation, result);
            }
            else {
              q.addFirst(continuation);
            }
            updated = true;
          }
        }
      }

      // continuation is not found -> add this chain as result if qualifier can be occurred with context variables
      if (!updated &&
          !context.getTarget().getClassQName().equals(headSignature.getOwner()) &&
          canQualifierOccurWithContextVariables(headSignature.getOwnerRef(), context, referenceServiceEx)) {
        addChainIfNotPresent(currentChain, result);
      }

      if (result.size() > maxResultSize) {
        return result;
      }
    }
    return result;
  }

  private static boolean canQualifierOccurWithContextVariables(@NotNull LightRef qualifier,
                                                               @NotNull ChainCompletionContext context,
                                                               @NotNull CompilerReferenceServiceEx referenceServiceEx) {
    for (LightRef ref: context.getContextClassReferences()) {
      if (referenceServiceEx.mayHappen(qualifier, ref, ChainSearchMagicConstants.VAR_PROBABILITY_THRESHOLD)) {
        return true;
      }
    }
    return false;
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
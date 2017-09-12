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

import java.util.*;

public class ChainSearcher {
  @NotNull
  public static List<CallChain> search(int pathMaximalLength,
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
        methods = unionSortedSet(currentMethods, methods);
      }
    }
    return new SearchInitializer(methods, context);
  }

  @NotNull
  private static List<CallChain> search(CompilerReferenceServiceEx referenceServiceEx,
                                        SearchInitializer initializer,
                                        int chainMaxLength,
                                        int maxResultSize,
                                        ChainCompletionContext context) {
    LinkedList<CallChain> q = initializer.getChainQueue();

    List<CallChain> result = new ArrayList<>();
    while (!q.isEmpty()) {

      ProgressManager.checkCanceled();
      CallChain currentChain = q.poll();
      RefChainOperation headSignature = currentChain.getHeadSignature();

      if (addChainIfTerminal(currentChain, result, chainMaxLength, context)) continue;

      // otherwise try to find chain continuation
      boolean updated = false;
      SortedSet<SignatureAndOccurrences> candidates = referenceServiceEx.findMethodReferenceOccurrences(headSignature.getOwner1(), SignatureData.ZERO_DIM);
      LightRef ref1 = headSignature.getRef1();
      for (SignatureAndOccurrences candidate : candidates) {
        if (candidate.getOccurrenceCount() * ChainSearchMagicConstants.FILTER_RATIO < currentChain.getChainWeight()) {
          break;
        }
        MethodIncompleteSignature sign = candidate.getSignature();
        if ((sign.isStatic() || !sign.getOwner().equals(context.getTarget().getClassQName())) &&
            (!(ref1 instanceof LightRef.JavaLightMethodRef) ||
             referenceServiceEx
               .mayHappen(candidate.getSignature().getRef(), ref1, ChainSearchMagicConstants.METHOD_PROBABILITY_THRESHOLD))) {

          CallChain continuation = currentChain.continuation(candidate.getSignature(), candidate.getOccurrenceCount(), context);
          if (continuation != null) {
            boolean stopChain =
              candidate.getSignature().isStatic() || context.hasQualifier(context.resolvePsiClass(candidate.getSignature().getOwnerRef()));
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

      if (ref1 instanceof LightRef.JavaLightMethodRef) {
        LightRef.LightClassHierarchyElementDef def =
          referenceServiceEx.mayCallOfTypeCast((LightRef.JavaLightMethodRef)ref1, ChainSearchMagicConstants.METHOD_PROBABILITY_THRESHOLD);
        if (def != null) {
          CallChain continuation = currentChain.continuationWithCast(new TypeCast(def, ((MethodIncompleteSignature)headSignature).getOwnerRef(), referenceServiceEx, 0), context);
          if (continuation != null) {
            q.addFirst(continuation);
            updated = true;
          }
        }
      }

      if (!updated) {
        addChainIfQualifierCanBeOccurredInContext(currentChain, result, context, referenceServiceEx);
      }

      if (result.size() > maxResultSize) {
        return result;
      }
    }
    return result;
  }

  /**
   * To reduce false-positives we add a method to result only if its qualifier can be occurred together with context variables.
   */
  private static void addChainIfQualifierCanBeOccurredInContext(CallChain currentChain,
                                                                List<CallChain> result,
                                                                ChainCompletionContext context,
                                                                CompilerReferenceServiceEx referenceServiceEx) {
    RefChainOperation signature = currentChain.getLastMethodSign();
    if (currentChain.hasCast()) return;
    if (!context.getTarget().getClassQName().equals(((MethodIncompleteSignature)signature).getOwner())) {
      Set<LightRef> references = context.getContextClassReferences();
      boolean isRelevantQualifier = false;
      for (LightRef ref: references) {
        if (referenceServiceEx.mayHappen(((MethodIncompleteSignature)signature).getOwnerRef(), ref, ChainSearchMagicConstants.VAR_PROBABILITY_THRESHOLD)) {
          isRelevantQualifier = true;
          break;
        }
      }

      if (references.isEmpty() || isRelevantQualifier) {
        addChainIfNotPresent(currentChain, result);
      }
    }
  }

  private static boolean addChainIfTerminal(CallChain currentChain, List<CallChain> result, int pathMaximalLength,
                                            ChainCompletionContext context) {
    RefChainOperation signature = currentChain.getLastMethodSign();
    RefChainOperation head = currentChain.getHeadSignature();
    if (((MethodIncompleteSignature)signature).isStatic() ||
        context.hasQualifier(context.resolvePsiClass(head.getOwnerRef1())) ||
        currentChain.length() >= pathMaximalLength) {
      addChainIfNotPresent(currentChain, result);
      return true;
    }
    return false;
  }

  private static void addChainIfNotPresent(CallChain newChain, List<CallChain> result) {
    if (result.isEmpty()) {
      result.add(newChain);
      return;
    }
    boolean doAdd = true;
    IntStack indicesToRemove = new IntStack();
    for (int i = 0; i < result.size(); i++) {
      CallChain chain = result.get(i);
      CallChain.CompareResult r = CallChain.compare(chain, newChain);
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

  private static <T> SortedSet<T> unionSortedSet(SortedSet<T> s1, SortedSet<T> s2) {
    if (s1.isEmpty()) return s2;
    if (s2.isEmpty()) return s1;
    TreeSet<T> result = new TreeSet<>();
    result.addAll(s1);
    result.addAll(s2);
    return result;
  }
}
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
  public static List<OperationChain> search(int pathMaximalLength,
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
    SortedSet<MethodRefAndOccurrences> methods = Collections.emptySortedSet();
    for (byte kind : target.getArrayKind()) {
      SortedSet<MethodRefAndOccurrences> currentMethods = referenceServiceEx.findMethodReferenceOccurrences(target.getClassQName(), kind, context);
      methods = methods == null ? currentMethods : unionSortedSet(currentMethods, methods);
    }
    return new SearchInitializer(methods, context);
  }

  @NotNull
  private static List<OperationChain> search(CompilerReferenceServiceEx referenceServiceEx,
                                             SearchInitializer initializer,
                                             int chainMaxLength,
                                             int maxResultSize,
                                             ChainCompletionContext context) {
    LinkedList<OperationChain> q = initializer.getChainQueue();

    List<OperationChain> result = new ArrayList<>();
    while (!q.isEmpty()) {

      ProgressManager.checkCanceled();
      OperationChain currentChain = q.poll();
      RefChainOperation head = currentChain.getHead();

      if (addChainIfTerminal(currentChain, result, chainMaxLength, context)) continue;

      // otherwise try to find chain continuation
      boolean updated = false;
      SortedSet<MethodRefAndOccurrences> candidates = referenceServiceEx.findMethodReferenceOccurrences(head.getQualifierRawName(), SignatureData.ZERO_DIM, context);
      LightRef ref = head.getLightRef();
      for (MethodRefAndOccurrences candidate : candidates) {
        if (candidate.getOccurrenceCount() * ChainSearchMagicConstants.FILTER_RATIO < currentChain.getChainWeight()) {
          break;
        }
        MethodCall sign = candidate.getSignature();
        if ((sign.isStatic() || !sign.getQualifierRawName().equals(context.getTarget().getClassQName())) &&
            (!(ref instanceof LightRef.JavaLightMethodRef) ||
             referenceServiceEx.mayHappen(candidate.getSignature().getLightRef(), ref, ChainSearchMagicConstants.METHOD_PROBABILITY_THRESHOLD))) {

          OperationChain
            continuation = currentChain.continuationWithMethod(candidate.getSignature(), candidate.getOccurrenceCount(), context);
          if (continuation != null) {
            boolean stopChain =
              candidate.getSignature().isStatic() || context.hasQualifier(context.resolvePsiClass(candidate.getSignature().getQualifierDef()));
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

      if (ref instanceof LightRef.JavaLightMethodRef) {
        LightRef.LightClassHierarchyElementDef def =
          referenceServiceEx.mayCallOfTypeCast((LightRef.JavaLightMethodRef)ref, ChainSearchMagicConstants.METHOD_PROBABILITY_THRESHOLD);
        if (def != null) {
          OperationChain
            continuation = currentChain.continuationWithCast(new TypeCast(def, head.getQualifierDef(), referenceServiceEx), context);
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
  private static void addChainIfQualifierCanBeOccurredInContext(OperationChain currentChain,
                                                                List<OperationChain> result,
                                                                ChainCompletionContext context,
                                                                CompilerReferenceServiceEx referenceServiceEx) {
    RefChainOperation signature = currentChain.getHeadMethodCall();
    // type cast + introduced qualifier: it's too complex chain
    if (currentChain.hasCast()) return;
    if (!context.getTarget().getClassQName().equals(signature.getQualifierRawName())) {
      Set<LightRef> references = context.getContextClassReferences();
      boolean isRelevantQualifier = false;
      for (LightRef ref: references) {
        if (referenceServiceEx.mayHappen(signature.getQualifierDef(), ref, ChainSearchMagicConstants.VAR_PROBABILITY_THRESHOLD)) {
          isRelevantQualifier = true;
          break;
        }
      }

      if (references.isEmpty() || isRelevantQualifier) {
        addChainIfNotPresent(currentChain, result);
      }
    }
  }

  private static boolean addChainIfTerminal(OperationChain currentChain, List<OperationChain> result, int pathMaximalLength,
                                            ChainCompletionContext context) {
    RefChainOperation signature = currentChain.getHeadMethodCall();
    RefChainOperation head = currentChain.getHead();
    if (((MethodCall)signature).isStatic() ||
        context.hasQualifier(context.resolvePsiClass(head.getQualifierDef())) ||
        currentChain.length() >= pathMaximalLength) {
      addChainIfNotPresent(currentChain, result);
      return true;
    }
    return false;
  }

  private static void addChainIfNotPresent(OperationChain newChain, List<OperationChain> result) {
    if (result.isEmpty()) {
      result.add(newChain);
      return;
    }
    boolean doAdd = true;
    IntStack indicesToRemove = new IntStack();
    for (int i = 0; i < result.size(); i++) {
      OperationChain chain = result.get(i);
      OperationChain.CompareResult r = OperationChain.compare(chain, newChain);
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
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMapper;

import java.util.Objects;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

/**
 * Possible side effects for the methods usually are handled via purity or mutability annotations
 * (see {@link DfaCallArguments#flush(DfaMemoryState, DfaValueFactory, PsiMethod)}): for pure method,
 * nothing is done. For impure methods, some or all qualified mutable variables are flushed.
 * This class allows custom handling (for example, updating the size of the collection on the 
 * {@link java.util.List#add(Object)} call).
 */
class SideEffectHandlers {
  private static final CallMapper<SideEffectHandler> HANDLERS = new CallMapper<SideEffectHandler>()
    // While list.set() produces a side effect (changes element), we don't track anything except size, 
    // so we don't need to flush anything
    .register(anyOf(instanceCall(JAVA_UTIL_LIST, "set").parameterTypes("int", "E")),
              (factory, state, arguments) -> { })
    .register(anyOf(instanceCall(JAVA_UTIL_COLLECTION, "clear").parameterCount(0),
                    instanceCall(JAVA_UTIL_MAP, "clear").parameterCount(0)),
              (factory, state, arguments) -> collectionClear(factory, state, arguments))
    .register(anyOf(instanceCall(JAVA_UTIL_LIST, "add").parameterTypes("E"),
                    instanceCall(JAVA_UTIL_LIST, "add").parameterTypes("int", "E")),
              (factory, state, arguments) -> collectionAdd(factory, state, arguments, true))
    .register(anyOf(instanceCall(JAVA_UTIL_SET, "add").parameterTypes("E"),
                    instanceCall(JAVA_UTIL_MAP, "put").parameterTypes("K", "V")),
              (factory, state, arguments) -> collectionAdd(factory, state, arguments, false))
    .register(anyOf(instanceCall(JAVA_UTIL_LIST, "addAll").parameterTypes(JAVA_UTIL_COLLECTION),
                    instanceCall(JAVA_UTIL_LIST, "addAll").parameterTypes("int", JAVA_UTIL_COLLECTION)),
              (factory, state, arguments) -> collectionAddAll(factory, state, arguments, true))
    .register(anyOf(instanceCall(JAVA_UTIL_SET, "addAll").parameterTypes(JAVA_UTIL_COLLECTION),
                    instanceCall(JAVA_UTIL_MAP, "putAll").parameterTypes(JAVA_UTIL_MAP)),
              (factory, state, arguments) -> collectionAddAll(factory, state, arguments, false))
    .register(anyOf(instanceCall(JAVA_UTIL_COLLECTION, "removeAll", "retainAll").parameterTypes(JAVA_UTIL_COLLECTION),
                    instanceCall(JAVA_UTIL_MAP, "removeAll").parameterTypes(JAVA_UTIL_MAP)),
              (factory, state, arguments) -> collectionReduce(factory, state, arguments))
    .register(anyOf(instanceCall(JAVA_UTIL_LIST, "remove").parameterTypes(JAVA_LANG_OBJECT),
                    instanceCall(JAVA_UTIL_SET, "remove").parameterTypes(JAVA_LANG_OBJECT),
                    instanceCall(JAVA_UTIL_MAP, "remove").parameterTypes(JAVA_LANG_OBJECT),
                    instanceCall(JAVA_UTIL_MAP, "remove").parameterTypes(JAVA_LANG_OBJECT, JAVA_LANG_OBJECT)),
              (factory, state, arguments) -> collectionRemove(factory, state, arguments, false))
    .register(instanceCall(JAVA_UTIL_LIST, "remove").parameterTypes("int"),
              (factory, state, arguments) -> collectionRemove(factory, state, arguments, true));

  private static void collectionAdd(DfaValueFactory factory, DfaMemoryState state, DfaCallArguments arguments, boolean list) {
    DfaVariableValue size = tryCast(SpecialField.COLLECTION_SIZE.createValue(factory, arguments.myQualifier), DfaVariableValue.class);
    if (size != null) {
      DfIntType sizeType = tryCast(state.getDfType(size), DfIntType.class);
      DfType resultSize = SpecialField.COLLECTION_SIZE.getDefaultValue(false);
      if (sizeType != null) {
        resultSize = sizeType.eval(DfTypes.intValue(1), LongRangeBinOp.PLUS).meet(DfTypes.intRange(LongRangeSet.indexRange()));
        if (!list) {
          resultSize = resultSize.join(sizeType.meet(DfTypes.intRange(LongRangeSet.range(1, Integer.MAX_VALUE))));
        }
        if (resultSize == DfTypes.BOTTOM) {
          // Possible int overflow
          resultSize = SpecialField.COLLECTION_SIZE.getDefaultValue(false);
        }
      }
      updateSize(state, size, resultSize);
    }
  }

  private static void collectionRemove(DfaValueFactory factory, DfaMemoryState state, DfaCallArguments arguments, boolean strict) {
    DfaVariableValue size = tryCast(SpecialField.COLLECTION_SIZE.createValue(factory, arguments.myQualifier), DfaVariableValue.class);
    if (size != null) {
      DfIntType sizeType = tryCast(state.getDfType(size), DfIntType.class);
      DfType resultSize = SpecialField.COLLECTION_SIZE.getDefaultValue(false);
      if (sizeType != null) {
        resultSize = sizeType.eval(DfTypes.intRange(LongRangeSet.range(strict ? 1 : 0, 1)), LongRangeBinOp.MINUS)
          .meet(DfTypes.intRange(LongRangeSet.indexRange()));
      }
      updateSize(state, size, resultSize);
    }
  }

  private static void collectionAddAll(DfaValueFactory factory, DfaMemoryState state, DfaCallArguments arguments, boolean list) {
    DfaVariableValue size = tryCast(SpecialField.COLLECTION_SIZE.createValue(factory, arguments.myQualifier), DfaVariableValue.class);
    DfaValue argSize = SpecialField.COLLECTION_SIZE.createValue(factory, ArrayUtil.getLastElement(arguments.myArguments));
    if (size != null) {
      DfIntType sizeType = tryCast(state.getDfType(size), DfIntType.class);
      DfType argSizeType = tryCast(state.getDfType(argSize), DfIntType.class);
      DfType resultSize = SpecialField.COLLECTION_SIZE.getDefaultValue(false);
      if (sizeType != null && argSizeType != null) {
        LongRangeSet totalRange = LongRangeSet.indexRange();
        if (!list) {
          LongRangeSet argSizeRange = DfIntType.extractRange(argSizeType);
          if (!argSizeRange.contains(0)) {
            // Adding non-empty collection to the set will produce non-empty set
            totalRange = totalRange.without(0);
          }
          LongRangeSet addedForSet = argSizeRange.fromRelation(RelationType.LE).intersect(LongRangeSet.indexRange());
          argSizeType = argSizeType.join(DfTypes.intRange(addedForSet));
        }
        resultSize = sizeType.eval(argSizeType, LongRangeBinOp.PLUS).meet(DfTypes.intRange(totalRange));
        if (resultSize == DfTypes.BOTTOM) {
          // Possible int overflow
          resultSize = SpecialField.COLLECTION_SIZE.getDefaultValue(false);
        }
      }
      updateSize(state, size, resultSize);
    }
  }

  private static void collectionReduce(DfaValueFactory factory, DfaMemoryState state, DfaCallArguments arguments) {
    DfaVariableValue size = tryCast(SpecialField.COLLECTION_SIZE.createValue(factory, arguments.myQualifier), DfaVariableValue.class);
    if (size != null) {
      DfIntType sizeType = tryCast(state.getDfType(size), DfIntType.class);
      DfType resultSize = SpecialField.COLLECTION_SIZE.getDefaultValue(false);
      if (sizeType != null) {
        LongRangeSet newSize = sizeType.getRange().fromRelation(RelationType.LE).intersect(LongRangeSet.indexRange());
        resultSize = sizeType.join(DfTypes.intRange(newSize));
      }
      updateSize(state, size, resultSize);
    }
  }

  private static void collectionClear(DfaValueFactory factory, DfaMemoryState state, DfaCallArguments arguments) {
    DfaVariableValue size = tryCast(SpecialField.COLLECTION_SIZE.createValue(factory, arguments.myQualifier), DfaVariableValue.class);
    if (size != null) {
      updateSize(state, size, DfTypes.intValue(0));
    }
  }

  /**
   * @param method
   * @return a custom side-effect handler for given method; null if not found
   */
  static SideEffectHandler getHandler(PsiMethod method) {
    return HANDLERS.mapFirst(method);
  }

  interface SideEffectHandler {
    /**
     * Apply side effects of the call to the supplied memory state. If handler is executed, default
     * processing (based on purity or mutation signature) is not executed.
     * 
     * @param factory value factory to use if necessary
     * @param state memory state to update
     * @param arguments call arguments
     */
    void handleSideEffect(DfaValueFactory factory, DfaMemoryState state, DfaCallArguments arguments);
  }
  
  private static void updateSize(DfaMemoryState state, DfaVariableValue var, DfType type) {
    // Dependent states may appear which we are not tracking (e.g. one visible list is sublist of another list)
    // so let's conservatively flush everything that could be affected
    state.flushFieldsQualifiedBy(Set.of(Objects.requireNonNull(var.getQualifier())));
    state.meetDfType(var, type);
  }
}

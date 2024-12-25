// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.ReachabilityCountingInterpreter;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.xdebugger.impl.dfaassist.DfaResult;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.Value;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class DebuggerDfaRunner {
  private final @NotNull PsiElement myBody;
  private final @NotNull Project myProject;
  private final @NotNull ControlFlow myFlow;
  private final @NotNull PsiElement myAnchor;
  private final @NotNull DfaInstructionState myStartingState;
  private final long myModificationStamp;
  private final DfaValueFactory myFactory;
  private final DfaAssistProvider myProvider;

  DebuggerDfaRunner(@NotNull Larva larva, @NotNull Map<Value, JdiValueInfo> infoMap) {
    myFactory = larva.myFactory;
    myBody = larva.myBody;
    myProject = larva.myProject;
    myProvider = larva.myProvider;
    myAnchor = larva.myAnchor;
    myFlow = larva.myFlow;
    DfaMemoryState state = createMemoryState(myFactory, larva.myJdiToDfa, infoMap);
    myStartingState = new DfaInstructionState(myFlow.getInstruction(larva.myOffset), state);
    myModificationStamp = larva.myStamp;
  }

  public @NotNull DfaResult computeHints() {
    if (PsiModificationTracker.getInstance(myProject).getModificationCount() != myModificationStamp) {
      return DfaResult.EMPTY;
    }
    var interceptor = myProvider.createListener();
    // computeHints() could be called several times in case if ReadAction is cancelled
    // So we need to copy the mutable myStartingState. Otherwise, restarted analysis will start from the wrong memory state
    DfaMemoryState memoryState = myStartingState.getMemoryState().createCopy();
    int startingIndex = myStartingState.getInstruction().getIndex();
    DfaInstructionState startingState = new DfaInstructionState(myStartingState.getInstruction(), memoryState);
    ReachabilityCountingInterpreter interpreter = new ReachabilityCountingInterpreter(myFlow, interceptor, true, false, startingIndex) {
      @Override
      protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
        DfaInstructionState[] states = super.acceptInstruction(instructionState);
        return StreamEx.of(states).filter(state -> state.getInstruction().getIndex() > startingIndex)
          .toArray(DfaInstructionState.EMPTY_ARRAY);
      }
    };
    if (interpreter.interpret(List.of(startingState)) != RunnerResult.OK) return DfaResult.EMPTY;
    Set<PsiElement> unreachable = interpreter.getUnreachable();
    Collection<TextRange> segments = DataFlowIRProvider.computeUnreachableSegments(myAnchor, unreachable);
    return new DfaResult(myBody.getContainingFile(), interceptor.computeHints(), segments);
  }

  /**
   * Larval state of DebuggerDfaRunner: read lock is required to create; limited JDI access is also used
   * (would be great to get rid of it in the future)
   */
  static class Larva {
    private final @NotNull Project myProject;
    private final @NotNull PsiElement myAnchor;
    private final @NotNull PsiElement myBody;
    private final @NotNull ControlFlow myFlow;
    private final @NotNull DfaValueFactory myFactory;
    private final long myStamp;
    private final @NotNull DfaAssistProvider myProvider;
    private final @NotNull Map<Value, List<DfaVariableValue>> myJdiToDfa;
    private final @NotNull StackFrameProxyEx myProxy;
    private final int myOffset;

    Larva(@NotNull Project project,
          @NotNull PsiElement anchor,
          @NotNull PsiElement body,
          @NotNull ControlFlow flow,
          @NotNull DfaValueFactory factory,
          long stamp,
          @NotNull DfaAssistProvider provider,
          @NotNull Map<Value, List<DfaVariableValue>> jdiToDfa,
          @NotNull StackFrameProxyEx proxy, int offset) {
      myProject = project;
      myAnchor = anchor;
      myBody = body;
      myFlow = flow;
      myFactory = factory;
      myStamp = stamp;
      myProvider = provider;
      myJdiToDfa = jdiToDfa;
      myProxy = proxy;
      myOffset = offset;
    }

    /**
     * Only JDI access (no read lock) is required to create a pupa
     */
    @NotNull Pupa pupate() throws EvaluateException {
      ApplicationManager.getApplication().assertReadAccessNotAllowed();
      return new Pupa(this);
    }
  }

  static class Pupa {
    private final @NotNull Larva myLarva;
    private final @NotNull Map<Value, JdiValueInfo> myInfoMap;

    Pupa(@NotNull Larva larva) throws EvaluateException {
      myLarva = larva;
      myInfoMap = requestJdi(larva.myProxy, larva.myJdiToDfa);
    }

    /**
     * No JDI access is required to create a final imago (but read lock is required)
     */
    @RequiresReadLock
    @Nullable DebuggerDfaRunner transform() {
      if (PsiModificationTracker.getInstance(myLarva.myProject).getModificationCount() != myLarva.myStamp) {
        return null;
      }
      return new DebuggerDfaRunner(myLarva, myInfoMap);
    }

    private static @NotNull Map<Value, JdiValueInfo> requestJdi(@NotNull StackFrameProxyEx proxy, @NotNull Map<Value, List<DfaVariableValue>> map)
      throws EvaluateException {
      ClassLoaderReference classLoader = proxy.getClassLoader();
      Predicate<ClassLoaderReference> classLoaderFilter = new Predicate<>() {
        private @Nullable List<ClassLoaderReference> myParentLoaders = null;

        @Override
        public boolean test(ClassLoaderReference loader) {
          if (loader == null || loader.equals(classLoader)) return true;
          return getParentLoaders().contains(loader);
        }

        private @NotNull List<ClassLoaderReference> getParentLoaders() {
          if (myParentLoaders == null) {
            List<ClassLoaderReference> loaders = Collections.emptyList();
            if (classLoader != null) {
              ClassType classLoaderClass = (ClassType)classLoader.referenceType();
              while (classLoaderClass != null && !"java.lang.ClassLoader".equals(classLoaderClass.name())) {
                classLoaderClass = classLoaderClass.superclass();
              }
              if (classLoaderClass != null) {
                Field parent = DebuggerUtils.findField(classLoaderClass, "parent");
                if (parent != null) {
                  loaders = StreamEx.iterate(
                      classLoader, Objects::nonNull, loader -> ObjectUtils.tryCast(loader.getValue(parent), ClassLoaderReference.class))
                    .toList();
                }
              }
            }
            myParentLoaders = loaders;
          }
          return myParentLoaders;
        }
      };

      return StreamEx.ofKeys(map)
        .mapToEntry(value -> JdiValueInfo.from(value, classLoaderFilter))
        .nonNullValues()
        .toMap();
    }
  }

  private @NotNull DfaMemoryState createMemoryState(@NotNull DfaValueFactory factory,
                                                    @NotNull Map<Value, List<DfaVariableValue>> valueMap,
                                                    @NotNull Map<Value, JdiValueInfo> infoMap) {
    DfaMemoryState state = new JvmDfaMemoryStateImpl(factory);
    List<DfaVariableValue> distinctValues = new ArrayList<>();
    valueMap.forEach((jdiValue, vars) -> {
      DfaVariableValue canonical = vars.get(0);
      if (!TypeConstraint.fromDfType(canonical.getDfType()).isComparedByEquals()) {
        distinctValues.add(canonical);
      }
      for (DfaVariableValue var : vars) {
        state.applyCondition(var.eq(canonical));
        addConditions(var, infoMap.get(jdiValue), state);
      }
    });
    EntryStream.ofPairs(distinctValues)
      .filterKeyValue((left, right) -> left.getDfType().meet(right.getDfType()) != DfType.BOTTOM)
      .limit(20) // avoid too complex state
      .forKeyValue((left, right) -> state.applyCondition(left.cond(RelationType.NE, right)));
    return state;
  }

  private void addConditions(@NotNull DfaVariableValue var, @Nullable JdiValueInfo valueInfo, @NotNull DfaMemoryState state) {
    if (valueInfo instanceof JdiValueInfo.PrimitiveConstant primitiveConstant) {
      ((DfaMemoryStateImpl)state).recordVariableType(var, primitiveConstant.getDfType());
    }
    else if (valueInfo instanceof JdiValueInfo.StringConstant stringConstant) {
      TypeConstraint stringType = myProvider.constraintFromJvmClassName(myBody, "java/lang/String");
      state.applyCondition(var.eq(DfTypes.referenceConstant(stringConstant.getValue(), stringType)));
    }
    else if (valueInfo instanceof JdiValueInfo.ObjectRef objectRef) {
      TypeConstraint exactType = getType(myBody, objectRef.getSignature());
      if (exactType == TypeConstraints.TOP) {
        state.meetDfType(var, DfTypes.NOT_NULL_OBJECT);
        return;
      }
      state.meetDfType(var, exactType.asDfType().meet(DfTypes.NOT_NULL_OBJECT));
      if (valueInfo instanceof JdiValueInfo.EnumConstant enumConstant) {
        String name = enumConstant.getName();
        if (exactType.isEnum()) {
          PsiClass enumClass = PsiUtil.resolveClassInClassTypeOnly(exactType.getPsiType(myProject));
          if (enumClass != null) {
            PsiField enumConst = enumClass.findFieldByName(name, false);
            if (enumConst instanceof PsiEnumConstant) {
              state.applyCondition(var.eq(DfTypes.referenceConstant(enumConst, exactType)));
            }
          }
        }
      }
      if (valueInfo instanceof JdiValueInfo.ObjectWithSpecialField withSpecialField) {
        SpecialField field = withSpecialField.getField();
        JdiValueInfo fieldValue = withSpecialField.getValue();
        DfaVariableValue dfaField = ObjectUtils.tryCast(field.createValue(myFactory, var), DfaVariableValue.class);
        if (dfaField != null) {
          addConditions(dfaField, fieldValue, state);
        }
      }
    }
  }

  private @NotNull TypeConstraint getType(@NotNull PsiElement context, @NotNull String signature) {
    int arrayDepth = 0;
    while (signature.length() > arrayDepth && signature.charAt(arrayDepth) == '[') {
      arrayDepth++;
    }
    TypeConstraint constraint = null;
    String nonArraySig = signature.substring(arrayDepth);
    if (nonArraySig.length() == 1) {
      if (arrayDepth > 0) {
        arrayDepth--;
      }
      PsiPrimitiveType primitiveType = PsiPrimitiveType.fromJvmTypeDescriptor(nonArraySig.charAt(0));
      if (primitiveType != null) {
        constraint = TypeConstraints.exact(primitiveType.createArrayType());
      }
    }
    else if (nonArraySig.startsWith("L")) {
      if (nonArraySig.endsWith(";")) {
        String jvmType = nonArraySig.substring(1, nonArraySig.length() - 1);
        constraint = myProvider.constraintFromJvmClassName(context, jvmType);
      }
    }
    if (constraint == null) return TypeConstraints.TOP;
    for (int i = 0; i < arrayDepth; i++) {
      constraint = constraint.arrayOf();
    }
    return constraint;
  }
}

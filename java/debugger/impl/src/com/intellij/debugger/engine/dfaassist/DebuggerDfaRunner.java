// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.sun.jdi.*;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DebuggerDfaRunner {
  private static final Set<String> COLLECTIONS_WITH_SIZE_FIELD = Set.of(
    CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_LINKED_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, "java.util.TreeMap");
  private final @NotNull PsiElement myBody;
  private final @NotNull PsiElement myAnchor;
  private final @NotNull Project myProject;
  private final @Nullable ControlFlow myFlow;
  private final @Nullable DfaInstructionState myStartingState;
  private final long myModificationStamp;
  private final DfaValueFactory myFactory;
  private final DfaAssistProvider myProvider;

  DebuggerDfaRunner(@NotNull DfaAssistProvider provider, @NotNull PsiElement body, @NotNull PsiElement anchor, @NotNull StackFrameProxyEx proxy) throws EvaluateException {
    myFactory = new DfaValueFactory(body.getProject());
    myBody = body;
    myAnchor = anchor;
    myProject = body.getProject();
    myProvider = provider;
    myFlow = DataFlowIRProvider.forElement(myBody, myFactory);
    myStartingState = getStartingState(proxy);
    myModificationStamp = PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount();
  }

  boolean isValid() {
    return myStartingState != null;
  }

  @Nullable
  public DebuggerDfaListener interpret() {
    if (myFlow == null || myStartingState == null ||
        PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount() != myModificationStamp) {
      return null;
    }
    var interceptor = myProvider.createListener();
    // interpret() could be called several times in case if ReadAction is cancelled
    // So we need to copy the mutable myStartingState. Otherwise, restarted analysis will start from the wrong memory state
    DfaMemoryState memoryState = myStartingState.getMemoryState().createCopy();
    DfaInstructionState startingState = new DfaInstructionState(myStartingState.getInstruction(), memoryState);
    StandardDataFlowInterpreter interpreter = new StandardDataFlowInterpreter(myFlow, interceptor, true);
    return interpreter.interpret(List.of(startingState)) == RunnerResult.OK ? interceptor : null;
  }

  @Nullable
  private DfaInstructionState getStartingState(@NotNull StackFrameProxyEx proxy) throws EvaluateException {
    if (myFlow == null) return null;
    int offset = myFlow.getStartOffset(myAnchor).getInstructionOffset();
    if (offset < 0) return null;
    DfaMemoryState state = new JvmDfaMemoryStateImpl(myFactory);
    StateBuilder builder = new StateBuilder(proxy, state);
    for (DfaValue dfaValue : myFactory.getValues().toArray(new DfaValue[0])) {
      if (dfaValue instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)dfaValue;
        builder.resolveJdi(var);
      }
    }
    builder.finish();
    if (builder.myChanged) {
      return new DfaInstructionState(myFlow.getInstruction(offset), state);
    }
    return null;
  }

  private static String getEnumConstantName(ObjectReference ref) {
    ReferenceType type = ref.referenceType();
    if (!(type instanceof ClassType) || !((ClassType)type).isEnum()) return null;
    ClassType superclass = ((ClassType)type).superclass();
    if (superclass == null) return null;
    if (!superclass.name().equals(CommonClassNames.JAVA_LANG_ENUM)) {
      superclass = superclass.superclass();
    }
    if (superclass == null || !superclass.name().equals(CommonClassNames.JAVA_LANG_ENUM)) return null;
    Field nameField = superclass.fieldByName("name");
    if (nameField == null) return null;
    Value nameValue = ref.getValue(nameField);
    return nameValue instanceof StringReference ? ((StringReference)nameValue).value() : null;
  }

  private class StateBuilder {
    private final @NotNull PsiElementFactory myPsiFactory = JavaPsiFacade.getElementFactory(myProject);
    private final @Nullable ClassLoaderReference myContextLoader;
    private final @NotNull DfaMemoryState myMemState;
    private final @NotNull Map<Value, DfaVariableValue> myCanonicalMap = new HashMap<>();
    private final @NotNull StackFrameProxyEx myProxy;
    private final @NotNull Location myLocation;
    private @Nullable List<ClassLoaderReference> myParentLoaders = null;
    private boolean myChanged;

    StateBuilder(@NotNull StackFrameProxyEx proxy, @NotNull DfaMemoryState memState) throws EvaluateException {
      myProxy = proxy;
      myLocation = proxy.location();
      myContextLoader = proxy.getClassLoader();
      myMemState = memState;
    }

    void resolveJdi(@NotNull DfaVariableValue var) throws EvaluateException {
      Value jdiValue = findJdiValue(var);
      if (jdiValue != null) {
        add(var, jdiValue);
      }
    }

    @Nullable
    private Value findJdiValue(@NotNull DfaVariableValue var) throws EvaluateException {
      if (var.getDescriptor() instanceof AssertionDisabledDescriptor) {
        ThreeState status = DebuggerUtilsEx.getEffectiveAssertionStatus(myLocation);
        // Assume that assertions are enabled if we cannot fetch the status
        return myLocation.virtualMachine().mirrorOf(status == ThreeState.NO);
      }
      return myProvider.getJdiValueForDfaVariable(myProxy, var, myAnchor);
    }

    void add(@NotNull DfaVariableValue var, @NotNull Value jdiValue) {
      DfaVariableValue canonicalVar = jdiValue instanceof ObjectReference ? myCanonicalMap.putIfAbsent(jdiValue, var) : null;
      if (canonicalVar != null) {
        myMemState.applyCondition(var.eq(canonicalVar));
      } else {
        addConditions(var, jdiValue);
      }
      myChanged = true;
    }

    void finish() {
      if (myChanged) {
        DfaVariableValue[] distinctValues = StreamEx.ofValues(myCanonicalMap)
            .filter(v -> !TypeConstraint.fromDfType(v.getDfType()).isComparedByEquals())
            .toArray(new DfaVariableValue[0]);
        EntryStream.ofPairs(distinctValues)
          .filterKeyValue((left, right) -> left.getDfType().meet(right.getDfType()) != DfType.BOTTOM)
          .limit(20) // avoid too complex state
          .forKeyValue((left, right) -> myMemState.applyCondition(left.cond(RelationType.NE, right)));
      }
    }

    private void addConditions(DfaVariableValue var, Value jdiValue) {
      DfType val = getConstantValue(jdiValue);
      if (val != DfType.TOP) {
        myMemState.applyCondition(var.eq(val));
      }
      if (jdiValue instanceof ObjectReference) {
        ObjectReference ref = (ObjectReference)jdiValue;
        ReferenceType type = ref.referenceType();
        ClassLoaderReference typeLoader = type.classLoader();
        if (!isCompatibleClassLoader(typeLoader)) return;
        PsiType psiType = getType(type, myProject, myBody.getResolveScope());
        if (psiType == null) return;
        TypeConstraint exactType = TypeConstraints.exact(psiType);
        String name = type.name();
        myMemState.meetDfType(var, exactType.asDfType().meet(DfTypes.NOT_NULL_OBJECT));
        if (jdiValue instanceof ArrayReference) {
          DfaValue dfaLength = SpecialField.ARRAY_LENGTH.createValue(myFactory, var);
          int jdiLength = ((ArrayReference)jdiValue).length();
          myMemState.applyCondition(dfaLength.eq(DfTypes.intValue(jdiLength)));
        }
        else if (TypeConversionUtil.isPrimitiveWrapper(name)) {
          setSpecialField(var, ref, type, "value", SpecialField.UNBOX);
        }
        else if (COLLECTIONS_WITH_SIZE_FIELD.contains(name)) {
          setSpecialField(var, ref, type, "size", SpecialField.COLLECTION_SIZE);
        }
        else if (name.startsWith("java.util.Collections$Empty")) {
          myMemState.applyCondition(SpecialField.COLLECTION_SIZE.createValue(myFactory, var).eq(DfTypes.intValue(0)));
        }
        else if (name.startsWith("java.util.Collections$Singleton")) {
          myMemState.applyCondition(SpecialField.COLLECTION_SIZE.createValue(myFactory, var).eq(DfTypes.intValue(1)));
        }
        else if (CommonClassNames.JAVA_UTIL_OPTIONAL.equals(name) && !(var.getDescriptor() instanceof SpecialField)) {
          setSpecialField(var, ref, type, "value", SpecialField.OPTIONAL_VALUE);
        }
      }
    }

    private boolean isCompatibleClassLoader(ClassLoaderReference loader) {
      if (loader == null || loader.equals(myContextLoader)) return true;
      return getParentLoaders().contains(loader);
    }

    @NotNull
    private List<ClassLoaderReference> getParentLoaders() {
      if (myParentLoaders == null) {
        List<ClassLoaderReference> loaders = Collections.emptyList();
        if (myContextLoader != null) {
          ClassType classLoaderClass = (ClassType)myContextLoader.referenceType();
          while (classLoaderClass != null && !"java.lang.ClassLoader".equals(classLoaderClass.name())) {
            classLoaderClass = classLoaderClass.superclass();
          }
          if (classLoaderClass != null) {
            Field parent = classLoaderClass.fieldByName("parent");
            if (parent != null) {
              loaders = StreamEx.iterate(
                myContextLoader, Objects::nonNull, loader -> ObjectUtils.tryCast(loader.getValue(parent), ClassLoaderReference.class)).toList();
            }
          }
        }
        myParentLoaders = loaders;
      }
      return myParentLoaders;
    }

    private void setSpecialField(DfaVariableValue dfaQualifier,
                                 ObjectReference jdiQualifier,
                                 ReferenceType type,
                                 String fieldName,
                                 SpecialField specialField) {
      Field value = type.fieldByName(fieldName);
      if (value != null) {
        DfaVariableValue dfaUnboxed = ObjectUtils.tryCast(specialField.createValue(myFactory, dfaQualifier), DfaVariableValue.class);
        Value jdiUnboxed = jdiQualifier.getValue(value);
        if (jdiUnboxed != null && dfaUnboxed != null) {
          addConditions(dfaUnboxed, jdiUnboxed);
        }
      }
    }

    @NotNull
    private DfType getConstantValue(Value jdiValue) {
      if (jdiValue == DfaAssistProvider.NullConst) {
        return DfTypes.NULL;
      }
      if (jdiValue instanceof BooleanValue) {
        return DfTypes.booleanValue(((BooleanValue)jdiValue).value());
      }
      if (jdiValue instanceof LongValue) {
        return DfTypes.longValue(((LongValue)jdiValue).longValue());
      }
      if (jdiValue instanceof ShortValue || jdiValue instanceof CharValue ||
          jdiValue instanceof ByteValue || jdiValue instanceof IntegerValue) {
        return DfTypes.intValue(((PrimitiveValue)jdiValue).intValue());
      }
      if (jdiValue instanceof FloatValue) {
        return DfTypes.floatValue(((FloatValue)jdiValue).floatValue());
      }
      if (jdiValue instanceof DoubleValue) {
        return DfTypes.doubleValue(((DoubleValue)jdiValue).doubleValue());
      }
      if (jdiValue instanceof StringReference) {
        PsiType stringType = myPsiFactory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, myBody.getResolveScope());
        return DfTypes.referenceConstant(((StringReference)jdiValue).value(), stringType);
      }
      if (jdiValue instanceof ObjectReference) {
        ReferenceType type = ((ObjectReference)jdiValue).referenceType();
        String enumConstantName = getEnumConstantName((ObjectReference)jdiValue);
        if (enumConstantName != null) {
          PsiType psiType = getType(type, myProject, myBody.getResolveScope());
          if (psiType instanceof PsiClassType) {
            PsiClass enumClass = ((PsiClassType)psiType).resolve();
            if (enumClass != null && enumClass.isEnum()) {
              PsiField enumConst = enumClass.findFieldByName(enumConstantName, false);
              if (enumConst instanceof PsiEnumConstant) {
                return DfTypes.referenceConstant(enumConst, psiType);
              }
            }
          }
        }
      }
      return DfType.TOP;
    }
  }

  private static @Nullable PsiType getType(@NotNull Type type,
                                           @NotNull Project project,
                                           @NotNull GlobalSearchScope scope) {
    if (type instanceof PrimitiveType) {
      String name = type.name();
      return PsiJavaParserFacadeImpl.getPrimitiveType(name);
    }
    else if (type instanceof ArrayType) {
      try {
        PsiType componentPsiType = getType(((ArrayType)type).componentType(), project, scope);
        return componentPsiType == null ? null : componentPsiType.createArrayType();
      }
      catch (ClassNotLoadedException e) {
        return null;
      }
    }
    else if (type instanceof ReferenceType) {
      PsiClass aClass = DebuggerUtils.findClass(type.name(), project, scope);
      if (aClass != null) {
        return JavaPsiFacade.getElementFactory(project).createType(aClass);
      }
    }
    return null;
  }
}

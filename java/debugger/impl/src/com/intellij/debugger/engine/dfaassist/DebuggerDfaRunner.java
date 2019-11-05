// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType.EQ;

class DebuggerDfaRunner extends DataFlowRunner {
  private static final Value NullConst = new Value() {
    @Override
    public VirtualMachine virtualMachine() { return null; }

    @Override
    public Type type() { return null; }

    @Override
    public String toString() { return "null"; }
  };
  private static final Set<String> COLLECTIONS_WITH_SIZE_FIELD =
    ContainerUtil.immutableSet(CommonClassNames.JAVA_UTIL_ARRAY_LIST,
                               "java.util.LinkedList",
                               CommonClassNames.JAVA_UTIL_HASH_MAP,
                               "java.util.TreeMap");
  private static final Map<String, Integer> COLLECTIONS_WITH_KNOWN_SIZE =
    EntryStream.of("java.util.Collections$EmptyList", 0,
                   "java.util.Collections$EmptySet", 0,
                   "java.util.Collections$EmptyMap", 0,
                   "java.util.Collections$Singleton", 1,
                   "java.util.Collections$SingletonList", 1,
                   "java.util.Collections$SingletonMap", 1).toImmutableMap();
  private final PsiCodeBlock myBody;
  private final PsiStatement myStatement;
  private final Project myProject;
  private final StackFrame myFrame;

  DebuggerDfaRunner(@NotNull PsiCodeBlock body, @NotNull PsiStatement statement, @NotNull StackFrame frame) {
    super(false, body);
    myBody = body;
    myStatement = statement;
    myProject = body.getProject();
    myFrame = frame;
  }

  @NotNull
  @Override
  protected List<DfaInstructionState> createInitialInstructionStates(@NotNull PsiElement psiBlock,
                                                                     @NotNull Collection<? extends DfaMemoryState> memStates,
                                                                     @NotNull ControlFlow flow) {
    if (psiBlock == myBody) {
      int offset = flow.getStartOffset(myStatement).getInstructionOffset();
      if (offset >= 0) {
        boolean changed = false;
        DfaMemoryState state = super.createMemoryState();
        PsiElementFactory psiFactory = JavaPsiFacade.getElementFactory(myProject);
        DfaValueFactory factory = getFactory();
        for (DfaValue dfaValue : factory.getValues().toArray(new DfaValue[0])) {
          if (dfaValue instanceof DfaVariableValue) {
            DfaVariableValue var = (DfaVariableValue)dfaValue;
            Value jdiValue = findJdiValue(var);
            if (jdiValue != null) {
              addToState(psiFactory, factory, state, var, jdiValue);
              changed = true;
            }
          }
        }
        if (changed) {
          return Collections.singletonList(new DfaInstructionState(flow.getInstruction(offset), state));
        }
      }
    }
    return Collections.emptyList(); // Cancel analysis: no reason to continue
  }

  @Nullable
  private Value findJdiValue(@NotNull DfaVariableValue var) {
    if (var.getQualifier() != null) {
      VariableDescriptor descriptor = var.getDescriptor();
      if (descriptor instanceof SpecialField) {
        // Special fields facts are applied from qualifiers
        return null;
      }
      Value qualifierValue = findJdiValue(var.getQualifier());
      if (qualifierValue == null) return null;
      PsiModifierListOwner element = descriptor.getPsiElement();
      if (element instanceof PsiField && qualifierValue instanceof ObjectReference) {
        ReferenceType type = ((ObjectReference)qualifierValue).referenceType();
        PsiClass psiClass = ((PsiField)element).getContainingClass();
        if (psiClass != null && type.name().equals(psiClass.getQualifiedName())) {
          Field field = type.fieldByName(((PsiField)element).getName());
          if (field != null) {
            return wrap(((ObjectReference)qualifierValue).getValue(field));
          }
        }
      }
      if (descriptor instanceof DfaExpressionFactory.ArrayElementDescriptor && qualifierValue instanceof ArrayReference) {
        int index = ((DfaExpressionFactory.ArrayElementDescriptor)descriptor).getIndex();
        int length = ((ArrayReference)qualifierValue).length();
        if (index >= 0 && index < length) {
          return wrap(((ArrayReference)qualifierValue).getValue(index));
        }
      }
      return null;
    }
    PsiModifierListOwner psi = var.getPsiVariable();
    if (psi instanceof PsiClass) {
      // this
      if (PsiTreeUtil.getParentOfType(myBody, PsiClass.class) == psi) {
        return myFrame.thisObject();
      }
      // TODO: support references to outer classes
    }
    if (psi instanceof PsiLocalVariable || psi instanceof PsiParameter) {
      try {
        LocalVariable variable = myFrame.visibleVariableByName(((PsiVariable)psi).getName());
        if (variable != null) {
          return wrap(myFrame.getValue(variable));
        }
      }
      catch (AbsentInformationException ignore) {
      }
    }
    return null;
  }

  private void addToState(PsiElementFactory psiFactory,
                          DfaValueFactory factory,
                          DfaMemoryState state, DfaVariableValue var,
                          Value jdiValue) {
    DfaConstValue val = getConstantValue(psiFactory, factory, jdiValue);
    if (val != null) {
      state.applyCondition(factory.createCondition(var, EQ, val));
    }
    if (jdiValue instanceof ObjectReference) {
      ObjectReference ref = (ObjectReference)jdiValue;
      ReferenceType type = ref.referenceType();
      PsiType psiType = getPsiReferenceType(psiFactory, type);
      if (psiType == null) return;
      TypeConstraint exactType = TypeConstraint.exact(factory.createDfaType(psiType));
      String name = type.name();
      state.applyFact(var, DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
      state.applyFact(var, DfaFactType.TYPE_CONSTRAINT, exactType);
      if (jdiValue instanceof ArrayReference) {
        DfaValue dfaLength = SpecialField.ARRAY_LENGTH.createValue(factory, var);
        int jdiLength = ((ArrayReference)jdiValue).length();
        state.applyCondition(factory.createCondition(dfaLength, EQ, factory.getInt(jdiLength)));
      }
      else if (TypeConversionUtil.isPrimitiveWrapper(name)) {
        setSpecialField(psiFactory, factory, state, var, ref, type, "value", SpecialField.UNBOX);
      }
      else if (COLLECTIONS_WITH_SIZE_FIELD.contains(name)) {
        setSpecialField(psiFactory, factory, state, var, ref, type, "size", SpecialField.COLLECTION_SIZE);
      }
      else if (name.startsWith("java.util.Collections$Empty")) {
        state.applyCondition(factory.createCondition(
          SpecialField.COLLECTION_SIZE.createValue(factory, var), EQ, factory.getInt(0)));
      }
      else if (name.startsWith("java.util.Collections$Singleton")) {
        state.applyCondition(factory.createCondition(
          SpecialField.COLLECTION_SIZE.createValue(factory, var), EQ, factory.getInt(1)));
      }
      else if (CommonClassNames.JAVA_UTIL_OPTIONAL.equals(name) && !(var.getDescriptor() instanceof SpecialField)) {
        setSpecialField(psiFactory, factory, state, var, ref, type, "value", SpecialField.OPTIONAL_VALUE);
      }
    }
  }

  @Nullable
  private PsiType getPsiReferenceType(PsiElementFactory psiFactory, ReferenceType jdiType) {
    Type componentType = jdiType;
    int depth = 0;
    while (componentType instanceof ArrayType) {
      try {
        componentType = ((ArrayType)componentType).componentType();
        depth++;
      }
      catch (ClassNotLoadedException e) {
        return null;
      }
    }
    PsiType psiType = psiFactory.createTypeByFQClassName(componentType.name(), myBody.getResolveScope());
    while (depth > 0) {
      psiType = psiType.createArrayType();
      depth--;
    }
    return psiType;
  }

  private void setSpecialField(PsiElementFactory psiFactory,
                               DfaValueFactory factory,
                               DfaMemoryState state,
                               DfaVariableValue dfaQualifier,
                               ObjectReference jdiQualifier,
                               ReferenceType type,
                               String fieldName,
                               SpecialField specialField) {
    Field value = type.fieldByName(fieldName);
    if (value != null) {
      DfaVariableValue dfaUnboxed = ObjectUtils.tryCast(specialField.createValue(factory, dfaQualifier), DfaVariableValue.class);
      Value jdiUnboxed = jdiQualifier.getValue(value);
      if (jdiUnboxed != null && dfaUnboxed != null) {
        addToState(psiFactory, factory, state, dfaUnboxed, jdiUnboxed);
      }
    }
  }

  @Nullable
  private DfaConstValue getConstantValue(PsiElementFactory psiFactory, DfaValueFactory factory, Value jdiValue) {
    if (jdiValue == NullConst) {
      return factory.getConstFactory().getNull();
    }
    if (jdiValue instanceof BooleanValue) {
      return factory.getBoolean(((BooleanValue)jdiValue).value());
    }
    if (jdiValue instanceof ShortValue || jdiValue instanceof CharValue ||
        jdiValue instanceof ByteValue || jdiValue instanceof IntegerValue) {
      return factory.getConstFactory().createFromValue(((PrimitiveValue)jdiValue).intValue(), PsiType.LONG);
    }
    if (jdiValue instanceof FloatValue) {
      return factory.getConstFactory().createFromValue(((FloatValue)jdiValue).floatValue(), PsiType.FLOAT);
    }
    if (jdiValue instanceof DoubleValue) {
      return factory.getConstFactory().createFromValue(((DoubleValue)jdiValue).doubleValue(), PsiType.DOUBLE);
    }
    if (jdiValue instanceof StringReference) {
      return factory.getConstFactory().createFromValue(
        ((StringReference)jdiValue).value(), psiFactory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING,
                                                                                myBody.getResolveScope()));
    }
    return null;
  }

  private static Value wrap(Value value) {
    return value == null ? NullConst : value;
  }
}

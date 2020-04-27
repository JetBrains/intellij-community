// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.expression.CaptureTraverser;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
                               CommonClassNames.JAVA_UTIL_LINKED_LIST,
                               CommonClassNames.JAVA_UTIL_HASH_MAP,
                               "java.util.TreeMap");
  private final @NotNull PsiElement myBody;
  private final @NotNull PsiElement myAnchor;
  private final @NotNull Project myProject;
  private final @Nullable ControlFlow myFlow;
  private final @Nullable DfaInstructionState myStartingState;
  private final long myModificationStamp;

  DebuggerDfaRunner(@NotNull PsiElement body, @NotNull PsiElement anchor, @NotNull StackFrame frame) {
    super(body.getProject(), body.getParent() instanceof PsiClassInitializer ? ((PsiClassInitializer)body.getParent()).getContainingClass() : body);
    myBody = body;
    myAnchor = anchor;
    myProject = body.getProject();
    myFlow = buildFlow(myBody);
    myStartingState = getStartingState(frame);
    myModificationStamp = PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount();
  }
  
  boolean isValid() {
    return myStartingState != null;
  }

  RunnerResult interpret(InstructionVisitor visitor) {
    if (myFlow == null || myStartingState == null || 
        PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount() != myModificationStamp) {
      return RunnerResult.ABORTED;
    }
    return interpret(myBody, visitor, myFlow, Collections.singletonList(myStartingState));
  }

  @Nullable
  private DfaInstructionState getStartingState(StackFrame frame) {
    if (myFlow == null) return null;
    int offset = myFlow.getStartOffset(myAnchor).getInstructionOffset();
    if (offset < 0) return null;
    DfaMemoryState state = super.createMemoryState();
    StateBuilder builder = new StateBuilder(frame, state);
    for (DfaValue dfaValue : getFactory().getValues().toArray(new DfaValue[0])) {
      if (dfaValue instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)dfaValue;
        Value jdiValue = findJdiValue(frame, var);
        if (jdiValue != null) {
          builder.add(var, jdiValue);
        }
      }
    }
    builder.finish();
    if (builder.myChanged) {
      return new DfaInstructionState(myFlow.getInstruction(offset), state);
    }
    return null;
  }

  @NotNull
  @Override
  protected DataFlowRunner.TimeStats createStatistics() {
    // Do not track time for DFA assist
    return new TimeStats(false);
  }

  @Nullable
  private Value findJdiValue(StackFrame frame, @NotNull DfaVariableValue var) {
    if (var.getQualifier() != null) {
      VariableDescriptor descriptor = var.getDescriptor();
      if (descriptor instanceof SpecialField) {
        // Special fields facts are applied from qualifiers
        return null;
      }
      Value qualifierValue = findJdiValue(frame, var.getQualifier());
      if (qualifierValue == null) return null;
      PsiModifierListOwner element = descriptor.getPsiElement();
      if (element instanceof PsiField && qualifierValue instanceof ObjectReference) {
        ReferenceType type = ((ObjectReference)qualifierValue).referenceType();
        PsiClass psiClass = ((PsiField)element).getContainingClass();
        if (psiClass != null && type.name().equals(JVMNameUtil.getClassVMName(psiClass))) {
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
    if (var.getDescriptor() instanceof DfaExpressionFactory.AssertionDisabledDescriptor) {
      ThreeState status = DebuggerUtilsEx.getEffectiveAssertionStatus(frame.location());
      // Assume that assertions are enabled if we cannot fetch the status
      return frame.virtualMachine().mirrorOf(status == ThreeState.NO);
    }
    PsiModifierListOwner psi = var.getPsiVariable();
    if (psi instanceof PsiClass) {
      // this
      PsiClass currentClass = PsiTreeUtil.getParentOfType(myBody, PsiClass.class);
      return CaptureTraverser.create((PsiClass)psi, currentClass, true).traverse(frame.thisObject());
    }
    if (psi instanceof PsiLocalVariable || psi instanceof PsiParameter) {
      String varName = ((PsiVariable)psi).getName();
      try {
        LocalVariable variable = frame.visibleVariableByName(varName);
        if (variable != null) {
          return wrap(frame.getValue(variable));
        }
      }
      catch (AbsentInformationException ignore) {
      }
      PsiClass currentClass = PsiTreeUtil.getParentOfType(myBody, PsiClass.class);
      PsiClass varClass = PsiTreeUtil.getParentOfType(psi, PsiClass.class);
      ObjectReference thisRef = CaptureTraverser.create(varClass, currentClass, false)
        .oneLevelLess().traverse(frame.thisObject());
      if (thisRef != null) {
        ReferenceType type = thisRef.referenceType();
        if (type instanceof ClassType) {
          Field field = type.fieldByName("val$" + varName);
          if (field != null) {
            return wrap(thisRef.getValue(field));
          }
        }
      }
    }
    if (psi instanceof PsiField && psi.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass psiClass = ((PsiField)psi).getContainingClass();
      if (psiClass != null) {
        String name = psiClass.getQualifiedName();
        if (name != null) {
          ReferenceType type = ContainerUtil.getOnlyItem(frame.virtualMachine().classesByName(name));
          if (type != null) {
            Field field = type.fieldByName(((PsiField)psi).getName());
            if (field != null && field.isStatic()) {
              return wrap(type.getValue(field));
            }
          }
        }
      }
    }
    return null;
  }

  private static Value wrap(Value value) {
    return value == null ? NullConst : value;
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
    private final @NotNull DfaValueFactory myFactory = getFactory();
    private final @Nullable ClassLoaderReference myContextLoader;
    private final @NotNull DfaMemoryState myMemState;
    private final @NotNull Map<Value, DfaVariableValue> myCanonicalMap = new HashMap<>();
    private @Nullable List<ClassLoaderReference> myParentLoaders = null;
    private boolean myChanged;

    StateBuilder(@NotNull StackFrame frame, @NotNull DfaMemoryState memState) {
      myContextLoader = frame.location().declaringType().classLoader();
      myMemState = memState;
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
            .filter(v -> v.getType() != null && !TypeConstraints.exact(v.getType()).isComparedByEquals())
            .toArray(new DfaVariableValue[0]);
        EntryStream.ofPairs(distinctValues)
          .filterKeyValue(
            (left, right) -> Objects.requireNonNull(left.getType()).isConvertibleFrom(Objects.requireNonNull(right.getType())))
          .limit(20) // avoid too complex state
          .forKeyValue((left, right) -> myMemState.applyCondition(left.cond(RelationType.NE, right)));
      }
    }

    private void addConditions(DfaVariableValue var, Value jdiValue) {
      DfType val = getConstantValue(jdiValue);
      if (val != DfTypes.TOP) {
        myMemState.applyCondition(var.eq(myFactory.fromDfType(val)));
      }
      if (jdiValue instanceof ObjectReference) {
        ObjectReference ref = (ObjectReference)jdiValue;
        ReferenceType type = ref.referenceType();
        ClassLoaderReference typeLoader = type.classLoader();
        if (!isCompatibleClassLoader(typeLoader)) return;
        PsiType psiType = getPsiReferenceType(myPsiFactory, type);
        if (psiType == null) return;
        TypeConstraint exactType = TypeConstraints.exact(psiType);
        String name = type.name();
        myMemState.meetDfType(var, exactType.asDfType().meet(DfTypes.NOT_NULL_OBJECT));
        if (jdiValue instanceof ArrayReference) {
          DfaValue dfaLength = SpecialField.ARRAY_LENGTH.createValue(myFactory, var);
          int jdiLength = ((ArrayReference)jdiValue).length();
          myMemState.applyCondition(dfaLength.eq(myFactory.getInt(jdiLength)));
        }
        else if (TypeConversionUtil.isPrimitiveWrapper(name)) {
          setSpecialField(var, ref, type, "value", SpecialField.UNBOX);
        }
        else if (COLLECTIONS_WITH_SIZE_FIELD.contains(name)) {
          setSpecialField(var, ref, type, "size", SpecialField.COLLECTION_SIZE);
        }
        else if (name.startsWith("java.util.Collections$Empty")) {
          myMemState.applyCondition(SpecialField.COLLECTION_SIZE.createValue(myFactory, var).eq(myFactory.getInt(0)));
        }
        else if (name.startsWith("java.util.Collections$Singleton")) {
          myMemState.applyCondition(SpecialField.COLLECTION_SIZE.createValue(myFactory, var).eq(myFactory.getInt(1)));
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
      if (jdiValue == NullConst) {
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
        return DfTypes.constant(((StringReference)jdiValue).value(), stringType);
      }
      if (jdiValue instanceof ObjectReference) {
        ReferenceType type = ((ObjectReference)jdiValue).referenceType();
        String enumConstantName = getEnumConstantName((ObjectReference)jdiValue);
        if (enumConstantName != null) {
          PsiType psiType = getPsiReferenceType(myPsiFactory, type);
          if (psiType instanceof PsiClassType) {
            PsiClass enumClass = ((PsiClassType)psiType).resolve();
            if (enumClass != null && enumClass.isEnum()) {
              PsiField enumConst = enumClass.findFieldByName(enumConstantName, false);
              if (enumConst instanceof PsiEnumConstant) {
                return DfTypes.constant(enumConst, psiType);
              }
            }
          }
        }
      }
      return DfTypes.TOP;
    }
  }
}

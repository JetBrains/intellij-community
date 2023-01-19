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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsParameterImpl;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.function.Function;

public abstract class ContractValue {
  // package private to avoid uncontrolled implementations
  ContractValue() {

  }

  abstract DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments);

  @NotNull
  public DfaCondition makeCondition(DfaValueFactory factory, DfaCallArguments arguments) {
    return DfaCondition.getUnknown();
  }

  public DfaCondition fromCall(DfaValueFactory factory, PsiCallExpression call) {
    DfaCallArguments arguments = DfaCallArguments.fromCall(factory, call);
    if (arguments == null) return DfaCondition.getUnknown();
    return makeCondition(factory, arguments);
  }

  /**
   * @param other other contract condition
   * @return true if this contract condition and other condition cannot be fulfilled at the same time
   */
  public boolean isExclusive(ContractValue other) {
    return false;
  }

  public ContractValue invert() {
    return null;
  }

  /**
   * @return true if this contract value represents a bounds-checking condition
   */
  public boolean isBoundCheckingCondition() {
    return false;
  }

  public OptionalInt getNullCheckedArgument(boolean equalToNull) {
    return getArgumentComparedTo(nullValue(), equalToNull);
  }

  public OptionalInt getArgumentComparedTo(ContractValue value, boolean equal) {
    return OptionalInt.empty();
  }

  public String getPresentationText(PsiCallExpression call) {
    return toString();
  }

  /**
   * @param call call to find the place in
   * @return the expression in the call that is the most relevant to the current value
   */
  public PsiExpression findPlace(PsiCallExpression call) {
    return null;
  }

  public @NotNull DfaCallState updateState(@NotNull DfaCallState state) {
    return state;
  }

  public static ContractValue qualifier() {
    return Qualifier.INSTANCE;
  }

  public static ContractValue argument(int index) {
    return new Argument(index);
  }

  public ContractValue specialField(@NotNull SpecialField field) {
    return new Spec(this, field);
  }

  public static ContractValue constant(Object value, @NotNull PsiType type) {
    return new IndependentValue(String.valueOf(value),
                                factory -> factory.fromDfType(DfTypes.constant(TypeConversionUtil.computeCastTo(value, type), type))
    );
  }

  public static ContractValue booleanValue(boolean value) {
    return value ? IndependentValue.TRUE : IndependentValue.FALSE;
  }

  public static ContractValue nullValue() {
    return IndependentValue.NULL;
  }

  public static ContractValue zero() {
    return IndependentValue.ZERO;
  }

  public static ContractValue condition(ContractValue left, RelationType relation, ContractValue right) {
    return new Condition(left, relation, right);
  }

  private static class Qualifier extends ContractValue {
    static final Qualifier INSTANCE = new Qualifier();

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return arguments.myQualifier;
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      if (call instanceof PsiMethodCallExpression) {
        return ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression();
      }
      return null;
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      PsiExpression place = findPlace(call);
      if (place != null) {
        return PsiExpressionTrimRenderer.render(place);
      }
      return super.getPresentationText(call);
    }

    @Override
    public @NotNull DfaCallState updateState(@NotNull DfaCallState state) {
      DfaValueFactory factory = state.getReturnValue().getFactory();
      DfaCallArguments callArguments = state.getCallArguments();
      DfaValue value = callArguments.myQualifier;
      if (!(value instanceof DfaVariableValue) && !DfaTypeValue.isUnknown(value) && !(value.getDfType() instanceof DfConstantType)) {
        DfaVariableValue var = makeVariable(state, factory, value);
        return state.withArguments(new DfaCallArguments(var, callArguments.myArguments, callArguments.myMutation));
      }
      return state;
    }

    @Override
    public String toString() {
      return "this";
    }
  }

  private static final class Argument extends ContractValue {
    private final int myIndex;

    Argument(int index) {
      myIndex = index;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      if (arguments.myArguments.length <= myIndex) {
        return factory.getUnknown();
      }
      return arguments.myArguments[myIndex];
    }

    @Override
    public @NotNull DfaCallState updateState(@NotNull DfaCallState state) {
      DfaValueFactory factory = state.getReturnValue().getFactory();
      DfaCallArguments callArguments = state.getCallArguments();
      DfaValue value = makeDfaValue(factory, callArguments);
      if (!(value instanceof DfaVariableValue) && !DfaTypeValue.isUnknown(value) && !(value.getDfType() instanceof DfConstantType)) {
        DfaVariableValue var = makeVariable(state, factory, value);
        DfaValue[] newArgs = callArguments.getArguments().clone();
        newArgs[myIndex] = var;
        return state.withArguments(new DfaCallArguments(callArguments.myQualifier, newArgs, callArguments.myMutation));
      }
      return state;
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      PsiExpressionList list = call.getArgumentList();
      if (list != null) {
        PsiExpression[] args = list.getExpressions();
        if (myIndex < args.length - 1 || (myIndex == args.length - 1 && !MethodCallUtils.isVarArgCall(call))) {
          return args[myIndex];
        }
      }
      return null;
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      PsiExpression place = findPlace(call);
      if (place != null && !ExpressionUtils.isNullLiteral(place)) {
        return PsiExpressionTrimRenderer.render(place);
      }
      PsiMethod method = call.resolveMethod();
      if (method == null) return toString();
      PsiParameter[] params = method.getParameterList().getParameters();
      if (myIndex == 0 && params.length == 1) {
        return JavaElementKind.PARAMETER.subject();
      }
      if (myIndex < params.length) {
        PsiParameter param = params[myIndex];
        if (param instanceof ClsParameterImpl && ((ClsParameterImpl)param).isAutoGeneratedName()) {
          return "param" + (myIndex + 1);
        }
        return param.getName();
      }
      return toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || (obj instanceof Argument && myIndex == ((Argument)obj).myIndex);
    }

    @Override
    public String toString() {
      return "param" + (myIndex + 1);
    }
  }

  private static class IndependentValue extends ContractValue {
    static final IndependentValue NULL = new IndependentValue("null", factory -> factory.fromDfType(DfTypes.NULL));
    static final IndependentValue TRUE = new IndependentValue("true", factory -> factory.fromDfType(DfTypes.TRUE)) {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == FALSE;
      }
    };
    static final IndependentValue FALSE = new IndependentValue("false", factory -> factory.fromDfType(DfTypes.FALSE)) {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == TRUE;
      }
    };
    static final IndependentValue ZERO = new IndependentValue("0", factory -> factory.fromDfType(DfTypes.intValue(0)));

    private final Function<? super DfaValueFactory, ? extends DfaValue> mySupplier;
    private final String myPresentation;

    IndependentValue(String presentation, Function<? super DfaValueFactory, ? extends DfaValue> supplier) {
      mySupplier = supplier;
      myPresentation = presentation;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return mySupplier.apply(factory);
    }

    @Override
    public String toString() {
      return myPresentation;
    }
  }

  private static final class Spec extends ContractValue {
    private final @NotNull ContractValue myQualifier;
    private final @NotNull SpecialField myField;

    Spec(@NotNull ContractValue qualifier, @NotNull SpecialField field) {
      myQualifier = qualifier;
      myField = field;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return myField.createValue(factory, myQualifier.makeDfaValue(factory, arguments));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof Spec)) return false;
      Spec that = (Spec)obj;
      return myQualifier.equals(that.myQualifier) && myField == that.myField;
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      return myQualifier.findPlace(call);
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      return JavaAnalysisBundle.message("dfa.find.cause.special.field.of.something", myField, myQualifier.getPresentationText(call));
    }

    @Override
    public String toString() {
      return myQualifier + "." + myField + "()";
    }
  }

  /**
   * A contract value that represents a relation between two other values
   */
  public static class Condition extends ContractValue {
    private final ContractValue myLeft, myRight;
    private final RelationType myRelationType;

    Condition(ContractValue left, RelationType type, ContractValue right) {
      myLeft = left;
      myRight = right;
      myRelationType = type;
    }

    @Override
    public boolean isBoundCheckingCondition() {
      return switch (myRelationType) {
        case LE, LT, GE, GT -> true;
        default -> false;
      };
    }

    @Override
    public boolean isExclusive(ContractValue other) {
      if (!(other instanceof Condition)) return false;
      Condition that = (Condition)other;
      if (that.myLeft.equals(myLeft) && that.myRight.equals(myRight) && that.myRelationType.getNegated() == myRelationType) {
        return true;
      }
      if (that.myLeft.equals(myRight) && that.myRight.equals(myLeft) && that.myRelationType.getNegated() == myRelationType.getFlipped()) {
        return true;
      }
      if (that.myRelationType == myRelationType) {
        if (that.myLeft.equals(myLeft) && that.myRight.isExclusive(myRight)) return true;
        if (that.myLeft.equals(myRight) && that.myRight.isExclusive(myLeft)) return true;
      }
      return false;
    }

    @Override
    public @NotNull DfaCallState updateState(@NotNull DfaCallState state) {
      return myRight.updateState(myLeft.updateState(state));
    }

    private @Nullable ContractValue getValueComparedTo(ContractValue value, boolean equal) {
      if (myRelationType == RelationType.equivalence(equal)) {
        ContractValue other;
        if (myLeft == value) {
          other = myRight;
        }
        else if (myRight == value) {
          other = myLeft;
        }
        else {
          return null;
        }
        return other;
      }
      if (value == IndependentValue.FALSE) {
        return getValueComparedTo(IndependentValue.TRUE, !equal);
      }
      return null;
    }

    @Override
    public OptionalInt getArgumentComparedTo(ContractValue value, boolean equal) {
      ContractValue other = getValueComparedTo(value, equal);
      return other instanceof Argument ? OptionalInt.of(((Argument)other).myIndex) : OptionalInt.empty();
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return factory.getUnknown();
    }

    @NotNull
    @Override
    public DfaCondition makeCondition(DfaValueFactory factory, DfaCallArguments arguments) {
      DfaValue left = myLeft.makeDfaValue(factory, arguments);
      DfaValue right = myRight.makeDfaValue(factory, arguments);
      if (left.getDfType() instanceof DfPrimitiveType) {
        right = DfaUtil.boxUnbox(right, left.getDfType());
      }
      if (right.getDfType() instanceof DfPrimitiveType) {
        left = DfaUtil.boxUnbox(left, right.getDfType());
      }
      return left.cond(myRelationType, right);
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      if (myLeft instanceof IndependentValue) {
        return myRight.getPresentationText(call) + " " + myRelationType.getFlipped() + " " + myLeft.getPresentationText(call);
      }
      return myLeft.getPresentationText(call) + " " + myRelationType + " " + myRight.getPresentationText(call);
    }

    /**
     * @return condition relation type
     */
    public @NotNull RelationType getRelationType() {
      return myRelationType;
    }

    /**
     * @return condition left operand
     */
    public @NotNull ContractValue getLeft() {
      return myLeft;
    }

    /**
     * @return condition right operand
     */
    public @NotNull ContractValue getRight() {
      return myRight;
    }

    @Override
    public ContractValue invert() {
      return new Condition(myLeft, myRelationType.getNegated(), myRight);
    }

    @Override
    public String toString() {
      return myLeft + " " + myRelationType + " " + myRight;
    }
  }

  private static class ContractTempDescriptor implements VariableDescriptor {
    private final @NotNull ContractValue myValue;
    private final @NotNull DfType myType;

    private ContractTempDescriptor(@NotNull ContractValue value, @NotNull DfType type) {
      myValue = value;
      myType = type;
    }

    @Override
    public boolean isStable() {
      return true;
    }

    @Override
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      return myType;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof ContractTempDescriptor &&
                            ((ContractTempDescriptor)obj).myValue == myValue &&
                            ((ContractTempDescriptor)obj).myType.equals(myType);
    }

    @Override
    public int hashCode() {
      return myValue.hashCode();
    }

    @Override
    public String toString() {
      return "contract$" + myValue;
    }
  }

  @NotNull DfaVariableValue makeVariable(@NotNull DfaCallState state, DfaValueFactory factory, DfaValue value) {
    DfType type = value.getDfType();
    if (type instanceof DfReferenceType) {
      if (type.isLocal()) {
        type = ((DfReferenceType)type).dropLocality();
        value = factory.fromDfType(type);
      }
      if (((DfReferenceType)type).getNullability() == DfaNullability.NULLABLE) {
        type = ((DfReferenceType)type).dropNullability();
      }
    }
    DfaVariableValue var = factory.getVarFactory().createVariableValue(new ContractTempDescriptor(this, type));
    state.getMemoryState().setVarValue(var, value);
    return var;
  }

  public static void flushContractTempVariables(DfaMemoryState state) {
    state.flushVariables(var -> var.getDescriptor() instanceof ContractTempDescriptor);
  }
}

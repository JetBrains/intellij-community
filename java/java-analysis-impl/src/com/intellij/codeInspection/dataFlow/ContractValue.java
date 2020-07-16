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

import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class ContractValue {
  // package private to avoid uncontrolled implementations
  ContractValue() {

  }

  abstract DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments);
  
  @NotNull
  DfaCondition makeCondition(DfaValueFactory factory, DfaCallArguments arguments) {
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

  public DfaCallArguments updateArguments(DfaCallArguments arguments, boolean negated) {
    return arguments;
  }

  public OptionalInt getNullCheckedArgument(boolean equalToNull) {
    return getArgumentComparedTo(nullValue(), equalToNull);
  }

  public OptionalInt getArgumentComparedTo(ContractValue value, boolean equal) {
    return OptionalInt.empty();
  }

  @NotNull DfaCallArguments fixArgument(@NotNull DfaCallArguments arguments, @NotNull UnaryOperator<DfType> converter) {
    return arguments;
  }

  public String getPresentationText(PsiMethod method) {
    return toString();
  }

  /**
   * @param call call to find the place in
   * @return the expression in the call that is the most relevant to the current value
   */
  public PsiExpression findPlace(PsiCallExpression call) {
    return null;
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
    return new IndependentValue(String.valueOf(value), factory -> factory.getConstant(TypeConversionUtil.computeCastTo(value, type), type)
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
    @NotNull DfaCallArguments fixArgument(@NotNull DfaCallArguments arguments, @NotNull UnaryOperator<DfType> converter) {
      if (arguments.myQualifier instanceof DfaTypeValue) {
        DfType type = arguments.myQualifier.getDfType();
        DfType newType = converter.apply(type);
        if (!type.equals(newType)) {
          return new DfaCallArguments(arguments.myQualifier.getFactory().fromDfType(newType), arguments.myArguments, arguments.myMutation);
        }
      }
      return arguments;
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
    public String getPresentationText(PsiMethod method) {
      PsiParameter[] params = method.getParameterList().getParameters();
      if (myIndex == 0 && params.length == 1) {
        return JavaElementKind.PARAMETER.subject();
      }
      if (myIndex < params.length) {
        return params[myIndex].getName();
      }
      return toString();
    }

    @Override
    @NotNull DfaCallArguments fixArgument(@NotNull DfaCallArguments arguments, @NotNull UnaryOperator<DfType> converter) {
      if (arguments.myArguments != null && arguments.myArguments.length > myIndex) {
        DfaValue value = arguments.myArguments[myIndex];
        if (value instanceof DfaTypeValue) {
          DfType type = value.getDfType();
          DfType newType = converter.apply(type);
          if (!type.equals(newType)) {
            DfaValue[] clone = arguments.myArguments.clone();
            clone[myIndex] = value.getFactory().fromDfType(newType);
            return new DfaCallArguments(arguments.myQualifier, clone, arguments.myMutation);
          }
        }
      }
      return arguments;
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
    static final IndependentValue NULL = new IndependentValue("null", factory -> factory.getNull());
    static final IndependentValue TRUE = new IndependentValue("true", factory -> factory.getBoolean(true)) {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == FALSE;
      }
    };
    static final IndependentValue FALSE = new IndependentValue("false", factory -> factory.getBoolean(false)) {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == TRUE;
      }
    };
    static final IndependentValue ZERO = new IndependentValue("0", factory -> factory.getInt(0));

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
    @NotNull DfaCallArguments fixArgument(@NotNull DfaCallArguments arguments, @NotNull UnaryOperator<DfType> converter) {
      return myQualifier.fixArgument(arguments, t -> {
        if (!(t instanceof DfReferenceType)) return t;
        DfType sfType = myField.getFromQualifier(t);
        DfType newType = converter.apply(sfType);
        return newType.equals(sfType) ? t : ((DfReferenceType)t).dropSpecialField().meet(myField.asDfType(newType));
      });
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      return myQualifier.findPlace(call);
    }

    @Override
    public String getPresentationText(PsiMethod method) {
      return myQualifier.getPresentationText(method) + "." + myField + (myField == SpecialField.ARRAY_LENGTH ? "" : "()");
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
      switch (myRelationType) {
        case LE:
        case LT:
        case GE:
        case GT:
          return true;
        default:
          return false;
      }
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
    public DfaCallArguments updateArguments(DfaCallArguments arguments, boolean negated) {
      ContractValue target = getValueComparedTo(nullValue(), negated);
      if (target != null) {
        return target.fixArgument(arguments, dfType -> dfType.meet(DfaNullability.NOT_NULL.asDfType()));
      }
      target = getValueComparedTo(nullValue(), !negated);
      if (target != null) {
        return target.fixArgument(arguments, dfType -> dfType.meet(DfaNullability.NULL.asDfType()));
      }
      return arguments;
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
    DfaCondition makeCondition(DfaValueFactory factory, DfaCallArguments arguments) {
      DfaValue left = myLeft.makeDfaValue(factory, arguments);
      DfaValue right = myRight.makeDfaValue(factory, arguments);
      if (left.getDfType() instanceof DfPrimitiveType) {
        right = DfaUtil.boxUnbox(right, left.getType());
      }
      if (right.getDfType() instanceof DfPrimitiveType) {
        left = DfaUtil.boxUnbox(left, right.getType());
      }
      return left.cond(myRelationType, right);
    }

    @Override
    public String getPresentationText(PsiMethod method) {
      if (myLeft instanceof IndependentValue) {
        return myRight.getPresentationText(method) + " " + myRelationType.getFlipped() + " " + myLeft.getPresentationText(method);
      }
      return myLeft.getPresentationText(method) + " " + myRelationType + " " + myRight.getPresentationText(method);
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
}

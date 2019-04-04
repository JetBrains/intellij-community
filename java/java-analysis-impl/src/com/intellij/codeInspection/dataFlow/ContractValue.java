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

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

public abstract class ContractValue {
  // package private to avoid uncontrolled implementations
  ContractValue() {

  }

  abstract DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments);
  
  public DfaValue fromCall(DfaValueFactory factory, PsiCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return DfaUnknownValue.getInstance();
    PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) return DfaUnknownValue.getInstance();
    DfaValue qualifierValue = null;
    if (call instanceof PsiMethodCallExpression) {
      PsiExpression qualifier = ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression();
      qualifierValue = factory.createValue(qualifier);
    }
    if (qualifierValue == null) {
      qualifierValue = DfaUnknownValue.getInstance();
    }
    boolean varArgCall = MethodCallUtils.isVarArgCall(call);
    PsiExpression[] args = argumentList.getExpressions();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    DfaValue[] argValues = new DfaValue[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      DfaValue argValue = null;
      if (i < args.length && (!varArgCall || i < parameters.length - 1)) {
        argValue = factory.createValue(args[i]);
      }
      if (argValue == null) {
        argValue = DfaUnknownValue.getInstance();
      }
      argValues[i] = argValue;
    }
    return makeDfaValue(factory, new DfaCallArguments(qualifierValue, argValues, JavaMethodContractUtil.isPure(method)));
  }

  /**
   * @param other other contract condition
   * @return true if this contract condition and other condition cannot be fulfilled at the same time
   */
  public boolean isExclusive(ContractValue other) {
    return false;
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
    return new IndependentValue(factory -> factory.getConstFactory().createFromValue(value, type), String.valueOf(value));
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

  public static ContractValue condition(ContractValue left, DfaRelationValue.RelationType relation, ContractValue right) {
    return new Condition(left, relation, right);
  }

  private static class Qualifier extends ContractValue {
    static final Qualifier INSTANCE = new Qualifier();

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return arguments.myQualifier;
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
        return DfaUnknownValue.getInstance();
      }
      return arguments.myArguments[myIndex];
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
    static final IndependentValue NULL = new IndependentValue(factory -> factory.getConstFactory().getNull(), "null");
    static final IndependentValue TRUE = new IndependentValue(factory -> factory.getConstFactory().getTrue(), "true") {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == FALSE;
      }
    };
    static final IndependentValue FALSE = new IndependentValue(factory -> factory.getConstFactory().getFalse(), "false") {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == TRUE;
      }
    };
    static final IndependentValue ZERO = new IndependentValue(factory -> factory.getInt(0), "0");

    private final Function<? super DfaValueFactory, ? extends DfaValue> mySupplier;
    private final String myPresentation;

    IndependentValue(Function<? super DfaValueFactory, ? extends DfaValue> supplier, String presentation) {
      mySupplier = supplier;
      myPresentation = presentation;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return mySupplier.fun(factory);
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
    public String toString() {
      return myQualifier + "." + myField + "()";
    }
  }

  private static class Condition extends ContractValue {
    private final ContractValue myLeft, myRight;
    private final DfaRelationValue.RelationType myRelationType;

    Condition(ContractValue left, DfaRelationValue.RelationType type, ContractValue right) {
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
      int index = getNullCheckedArgument(negated).orElse(-1);
      if (index >= 0 && index < arguments.myArguments.length) {
        DfaValue arg = arguments.myArguments[index];
        if (arg instanceof DfaFactMapValue) {
          DfaValue newArg = ((DfaFactMapValue)arg).withFact(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
          if (newArg != arg) {
            DfaValue[] newArguments = arguments.myArguments.clone();
            newArguments[index] = newArg;
            return new DfaCallArguments(arguments.myQualifier, newArguments, arguments.myPure);
          }
        }
      }
      return arguments;
    }

    @Override
    public OptionalInt getArgumentComparedTo(ContractValue value, boolean equal) {
      if (myRelationType == DfaRelationValue.RelationType.equivalence(equal)) {
        ContractValue other;
        if (myLeft == value) {
          other = myRight;
        }
        else if (myRight == value) {
          other = myLeft;
        }
        else {
          return OptionalInt.empty();
        }
        if (other instanceof Argument) {
          return OptionalInt.of(((Argument)other).myIndex);
        }
      }
      if (value == IndependentValue.FALSE) {
        return getArgumentComparedTo(IndependentValue.TRUE, !equal);
      }
      return OptionalInt.empty();
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      DfaValue left = myLeft.makeDfaValue(factory, arguments);
      DfaValue right = myRight.makeDfaValue(factory, arguments);
      if (left instanceof DfaConstValue && left.getType() instanceof PsiPrimitiveType) {
        right = DfaUtil.boxUnbox(right, left.getType());
      }
      if (right instanceof DfaConstValue && right.getType() instanceof PsiPrimitiveType) {
        left = DfaUtil.boxUnbox(left, right.getType());
      }
      return factory.createCondition(left, myRelationType, right);
    }

    @Override
    public String toString() {
      return myLeft + " " + myRelationType + " " + myRight;
    }
  }
}

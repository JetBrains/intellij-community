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

import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;

/**
 * @author Tagir Valeev
 */
public abstract class ContractValue {
  // package private to avoid uncontrolled implementations
  ContractValue() {

  }

  abstract DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments);

  /**
   * @return true if this contract value represents a bounds-checking condition
   */
  public boolean isBoundCheckingCondition() {
    return false;
  }

  public static ContractValue qualifier() {
    return Qualifier.INSTANCE;
  }

  public static ContractValue argument(int index) {
    return new Argument(index);
  }

  public ContractValue specialField(SpecialField field) {
    return new Spec(this, field);
  }

  public static ContractValue constant(Object value, PsiType type) {
    return new IndependentValue(factory -> factory.getConstFactory().createFromValue(value, type, null), String.valueOf(value));
  }

  public static ContractValue booleanValue(boolean value) {
    return value ? IndependentValue.TRUE : IndependentValue.FALSE;
  }

  public static ContractValue optionalValue(boolean present) {
    return present ? IndependentValue.OPTIONAL_PRESENT : IndependentValue.OPTIONAL_ABSENT;
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

  private static class Argument extends ContractValue {
    private final int myIndex;

    Argument(int index) {
      myIndex = index;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return arguments.myArguments.length <= myIndex ? DfaUnknownValue.getInstance() : arguments.myArguments[myIndex];
    }

    @Override
    public String toString() {
      return "arg#" + myIndex;
    }
  }

  private static class IndependentValue extends ContractValue {
    static final IndependentValue NULL = new IndependentValue(factory -> factory.getConstFactory().getNull(), "null");
    static final IndependentValue TRUE = new IndependentValue(factory -> factory.getConstFactory().getTrue(), "true");
    static final IndependentValue FALSE = new IndependentValue(factory -> factory.getConstFactory().getFalse(), "false");
    static final IndependentValue OPTIONAL_PRESENT =
      new IndependentValue(factory -> factory.getFactValue(DfaFactType.OPTIONAL_PRESENCE, true), "present");
    static final IndependentValue OPTIONAL_ABSENT =
      new IndependentValue(factory -> factory.getFactValue(DfaFactType.OPTIONAL_PRESENCE, false), "empty");
    static final IndependentValue ZERO = new IndependentValue(factory -> factory.getInt(0), "0");

    private final Function<DfaValueFactory, DfaValue> mySupplier;
    private final String myPresentation;

    IndependentValue(Function<DfaValueFactory, DfaValue> supplier, String presentation) {
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

  private static class Spec extends ContractValue {
    private final ContractValue myQualifier;
    private final SpecialField myField;

    Spec(ContractValue qualifier, SpecialField field) {
      myQualifier = qualifier;
      myField = field;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return myField.createValue(factory, myQualifier.makeDfaValue(factory, arguments));
    }

    @Override
    public String toString() {
      return myQualifier + "." + myField.getMethodName() + "()";
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
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return factory.createCondition(myLeft.makeDfaValue(factory, arguments), myRelationType, myRight.makeDfaValue(factory, arguments));
    }

    @Override
    public String toString() {
      return myLeft + " " + myRelationType + " " + myRight;
    }
  }
}

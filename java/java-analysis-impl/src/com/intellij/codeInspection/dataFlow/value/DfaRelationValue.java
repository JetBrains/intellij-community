/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 6, 2002
 * Time: 10:01:02 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.codeInspection.dataFlow.value.DfaRelation.*;

public class DfaRelationValue extends DfaValue {

  private final DfaValueFactory myFactory;
  private DfaValue myLeftOperand;
  private DfaValue myRightOperand;
  protected DfaRelation myRelation;
  protected boolean myIsNegated;

  public static class Factory {
    protected final DfaRelationValue mySharedInstance;
    protected final HashMap<String, ArrayList<DfaRelationValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaRelationValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaRelationValue>>();
    }

    public DfaRelationValue createRelation(DfaValue dfaLeft, DfaValue dfaRight, DfaRelation relation, boolean negated) {
      if (PLUS == relation) return null;

      if (dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaUnboxedValue
          || dfaRight instanceof DfaVariableValue || dfaRight instanceof DfaBoxedValue || dfaRight instanceof DfaUnboxedValue) {
        if (!(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaUnboxedValue)) {
          return createRelation(dfaRight, dfaLeft, getSymmetricOperation(relation), negated);
        }

        return createCanonicalRelation(relation, negated, dfaLeft, dfaRight);
      }
      if (dfaLeft instanceof DfaTypeValue && ((DfaTypeValue)dfaLeft).isNotNull() && dfaRight instanceof DfaConstValue) {
        return createCanonicalRelation(relation, negated, dfaLeft, dfaRight);
      }
      else if (dfaRight instanceof DfaTypeValue && ((DfaTypeValue)dfaRight).isNotNull() && dfaLeft instanceof DfaConstValue) {
        return createCanonicalRelation(relation, negated, dfaRight, dfaLeft);
      }
      else {
        return null;
      }
    }

    private DfaRelationValue createCanonicalRelation(DfaRelation relation,
                                                     boolean negated,
                                                     @NotNull final DfaValue dfaLeft,
                                                     @NotNull final DfaValue dfaRight) {
      // To canonical form.
      if (NE == relation) {
        relation = EQ;
        negated = !negated;
      }
      else if (LT == relation) {
        relation = GE;
        negated = !negated;
      }
      else if (LE == relation) {
        relation = GT;
        negated = !negated;
      }

      mySharedInstance.myLeftOperand = dfaLeft;
      mySharedInstance.myRightOperand = dfaRight;
      mySharedInstance.myRelation = relation;
      mySharedInstance.myIsNegated = negated;

      String id = mySharedInstance.toString();
      ArrayList<DfaRelationValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaRelationValue>();
        myStringToObject.put(id, conditions);
      }
      else {
        for (DfaRelationValue rel : conditions) {
          if (rel.hardEquals(mySharedInstance)) return rel;
        }
      }

      DfaRelationValue result = new DfaRelationValue(dfaLeft, dfaRight, relation, negated, myFactory);
      conditions.add(result);
      return result;
    }

  }

  @Nullable
  public static DfaRelation getSymmetricOperation(DfaRelation sign) {
    if (LT == sign) return GT;
    if (GE == sign) return LE;
    if (GT == sign) return LT;
    if (LE == sign) return GE;
    return sign;
  }

  protected DfaRelationValue(DfaValueFactory factory) {
    super(factory);
    myFactory = factory;
  }

  protected DfaRelationValue(DfaValue leftOperand,
                             DfaValue rightOperand,
                             DfaRelation relation,
                             boolean myIsNegated,
                             DfaValueFactory factory) {
    this(factory);
    this.myLeftOperand = leftOperand;
    this.myRightOperand = rightOperand;
    this.myRelation = relation;
    this.myIsNegated = myIsNegated;
  }

  public DfaValue getLeftOperand() {
    return myLeftOperand;
  }

  public DfaValue getRightOperand() {
    return myRightOperand;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Override
  public DfaValue createNegated() {
    return myFactory.getRelationFactory().createRelation(myLeftOperand, myRightOperand, myRelation, !myIsNegated);
  }

  private boolean hardEquals(DfaRelationValue rel) {
    return Comparing.equal(rel.myLeftOperand, myLeftOperand)
           && Comparing.equal(rel.myRightOperand, myRightOperand) &&
           rel.myRelation == myRelation &&
           rel.myIsNegated == myIsNegated;
  }

  public boolean isEquality() {
    return myRelation == EQ && !myIsNegated;
  }

  public boolean isNonEquality() {
    return myRelation == EQ && myIsNegated || myRelation == GT && !myIsNegated || myRelation == GE && myIsNegated;
  }

  public boolean isInstanceOf() {
    return myRelation == INSTANCEOF;
  }

  @Override
  public String toString() {
    return myLeftOperand + " " + (isNegated() ? "not " : "") + myRelation + " " + myRightOperand;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static com.intellij.psi.JavaTokenType.*;

public class DfaRelationValue extends DfaValue {
  private DfaValue myLeftOperand;
  private DfaValue myRightOperand;
  private IElementType myRelation;
  private boolean myIsNegated;

  public static class Factory {
    private final DfaRelationValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaRelationValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaRelationValue(factory);
      myStringToObject = new HashMap<>();
    }

    public DfaRelationValue createRelation(DfaValue dfaLeft, DfaValue dfaRight, IElementType relation, boolean negated) {
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

    private DfaRelationValue createCanonicalRelation(IElementType relation,
                                                     boolean negated,
                                                     @NotNull final DfaValue dfaLeft,
                                                     @NotNull final DfaValue dfaRight) {
      // To canonical form.
      if (NE == relation) {
        relation = EQEQ;
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
        conditions = new ArrayList<>();
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

  public static IElementType getSymmetricOperation(IElementType sign) {
    if (LT == sign) return GT;
    if (GE == sign) return LE;
    if (GT == sign) return LT;
    if (LE == sign) return GE;
    return sign;
  }

  private DfaRelationValue(DfaValueFactory factory) {
    super(factory);
  }

  private DfaRelationValue(DfaValue myLeftOperand, DfaValue myRightOperand, IElementType myRelation, boolean myIsNegated,
                           DfaValueFactory factory) {
    super(factory);
    this.myLeftOperand = myLeftOperand;
    this.myRightOperand = myRightOperand;
    this.myRelation = myRelation;
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
    return Comparing.equal(rel.myLeftOperand,myLeftOperand)
      && Comparing.equal(rel.myRightOperand,myRightOperand) &&
      rel.myRelation == myRelation &&
      rel.myIsNegated == myIsNegated;
  }

  public boolean isEquality() {
    return myRelation == EQEQ && !myIsNegated;
  }

  public boolean isNonEquality() {
    return myRelation == EQEQ && myIsNegated || myRelation == GT && !myIsNegated || myRelation == GE && myIsNegated;
  }

  public boolean isInstanceOf() {
    return myRelation == INSTANCEOF_KEYWORD;
  }

  @NonNls public String toString() {
    return (isNegated() ? "not " : "") + myLeftOperand + " " + myRelation + " " + myRightOperand;
  }
}

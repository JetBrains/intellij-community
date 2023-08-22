// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DfaMemoryStateImplTest extends LightJavaCodeInsightTestCase {
  static final class MyDescriptor implements VariableDescriptor {
    private final String myName;

    MyDescriptor(String name) { myName = name; }

    @Override
    public boolean isStable() {
      return true;
    }

    @Override
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      return DfTypes.INT;
    }

    @Override
    public String toString() {
      return myName;
    }
  }
  
  public void testMergeLessThanRelation() {
    DfaValueFactory factory = new DfaValueFactory(getProject());
    DfaVariableValue a = factory.getVarFactory().createVariableValue(new MyDescriptor("a"));
    DfaVariableValue b = factory.getVarFactory().createVariableValue(new MyDescriptor("b"));
    DfaMemoryState left = new JvmDfaMemoryStateImpl(factory);
    DfaMemoryState right = left.createCopy();
    assertTrue(left.applyCondition(a.cond(RelationType.LT, b)));
    assertEquals(RelationType.LT, left.getRelation(a, b));
    assertEquals("int <= Integer.MAX_VALUE-1", left.getDfType(a).toString());
    assertEquals("int >= Integer.MIN_VALUE+1", left.getDfType(b).toString());
    right.applyCondition(a.cond(RelationType.GT, b));
    assertEquals(RelationType.GT, right.getRelation(a, b));
    assertEquals("int >= Integer.MIN_VALUE+1", right.getDfType(a).toString());
    assertEquals("int <= Integer.MAX_VALUE-1", right.getDfType(b).toString());
    DfaMemoryState result = left.tryJoinExactly(right);
    assertNotNull(result);
    assertEquals(RelationType.NE, result.getRelation(a, b));
    assertEquals("int", result.getDfType(a).toString());
    assertEquals("int", result.getDfType(b).toString());
    DfaMemoryState result2 = right.tryJoinExactly(left);
    assertNotNull(result2);
    assertEquals(RelationType.NE, result2.getRelation(a, b));
    assertEquals("int", result2.getDfType(a).toString());
    assertEquals("int", result2.getDfType(b).toString());
    assertEquals(result, result2);
  }
  
  public void testMergeLessThanRelationConstrained() {
    DfaValueFactory factory = new DfaValueFactory(getProject());
    DfaVariableValue a = factory.getVarFactory().createVariableValue(new MyDescriptor("a"));
    DfaVariableValue b = factory.getVarFactory().createVariableValue(new MyDescriptor("b"));
    DfaMemoryStateImpl left = new JvmDfaMemoryStateImpl(factory);
    assertTrue(left.applyCondition(a.cond(RelationType.GT, factory.fromDfType(DfTypes.intValue(10)))));
    assertTrue(left.applyCondition(b.cond(RelationType.LT, factory.fromDfType(DfTypes.intValue(1000)))));
    DfaMemoryStateImpl right = left.createCopy();
    assertTrue(left.applyCondition(a.cond(RelationType.LT, b)));
    assertEquals(RelationType.LT, left.getRelation(a, b));
    assertEquals("int in {11..998}", left.getDfType(a).toString());
    assertEquals("int in {12..999}", left.getDfType(b).toString());
    right.applyCondition(a.cond(RelationType.GT, b));
    assertEquals(RelationType.GT, right.getRelation(a, b));
    assertEquals("int >= 11", right.getDfType(a).toString());
    assertEquals("int <= 999", right.getDfType(b).toString());
    DfaMemoryState result = left.tryJoinExactly(right);
    assertNotNull(result);
    assertEquals(RelationType.NE, result.getRelation(a, b));
    assertEquals("int >= 11", result.getDfType(a).toString());
    assertEquals("int <= 999", result.getDfType(b).toString());
    DfaMemoryState result2 = right.tryJoinExactly(left);
    assertNotNull(result2);
    assertEquals(RelationType.NE, result2.getRelation(a, b));
    assertEquals("int >= 11", result2.getDfType(a).toString());
    assertEquals("int <= 999", result2.getDfType(b).toString());
    assertEquals(result, result2);
  }
}

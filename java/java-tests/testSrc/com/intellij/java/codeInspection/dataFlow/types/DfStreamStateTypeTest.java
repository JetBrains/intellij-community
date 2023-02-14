// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.junit.Test;

import static com.intellij.codeInspection.dataFlow.types.DfStreamStateType.*;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DfStreamStateTypeTest {

  @Test
  public void isSuperType() {
    assertTrue(UNKNOWN.isSuperType(CONSUMED));
    assertTrue(UNKNOWN.isSuperType(OPEN));
    assertTrue(OPEN.isSuperType(OPEN));
    assertFalse(OPEN.isSuperType(CONSUMED));
    assertFalse(OPEN.isSuperType(UNKNOWN));
    assertFalse(CONSUMED.isSuperType(UNKNOWN));
    assertFalse(CONSUMED.isSuperType(OPEN));
  }

  @Test
  public void join() {
    assertEquals(UNKNOWN, UNKNOWN.join(CONSUMED));
    assertEquals(OPEN, OPEN.join(OPEN));
    assertEquals(CONSUMED, CONSUMED.join(CONSUMED));
    assertEquals(UNKNOWN, OPEN.join(CONSUMED));
    assertEquals(UNKNOWN, OPEN.join(UNKNOWN));
  }

  @Test
  public void tryJoinExactly() {
    assertEquals(UNKNOWN, UNKNOWN.tryJoinExactly(CONSUMED));
    assertEquals(OPEN, OPEN.tryJoinExactly(OPEN));
    assertEquals(CONSUMED, CONSUMED.tryJoinExactly(CONSUMED));
    assertNull(OPEN.tryJoinExactly(CONSUMED));
    assertEquals(UNKNOWN, OPEN.tryJoinExactly(UNKNOWN));
  }

  @Test
  public void meet() {
    assertEquals(CONSUMED, UNKNOWN.meet(CONSUMED));
    assertEquals(OPEN, OPEN.meet(OPEN));
    assertEquals(CONSUMED, CONSUMED.meet(CONSUMED));
    assertEquals(BOTTOM, OPEN.meet(CONSUMED));
    assertEquals(OPEN, OPEN.meet(UNKNOWN));
  }

  @Test
  public void meetRelation() {
    assertEquals(CONSUMED, UNKNOWN.meetRelation(RelationType.EQ, CONSUMED));
    assertEquals(OPEN, OPEN.meetRelation(RelationType.EQ, OPEN));
    assertEquals(CONSUMED, CONSUMED.meetRelation(RelationType.EQ, CONSUMED));
    assertEquals(BOTTOM, OPEN.meetRelation(RelationType.EQ, CONSUMED));
    assertEquals(OPEN, OPEN.meetRelation(RelationType.EQ, UNKNOWN));

    assertEquals(OPEN, UNKNOWN.meetRelation(RelationType.NE, CONSUMED));
    assertEquals(BOTTOM, OPEN.meetRelation(RelationType.NE, OPEN));
    assertEquals(BOTTOM, CONSUMED.meetRelation(RelationType.NE, CONSUMED));
    assertEquals(OPEN, OPEN.meetRelation(RelationType.NE, CONSUMED));
    assertEquals(OPEN, OPEN.meetRelation(RelationType.NE, UNKNOWN));
  }
}

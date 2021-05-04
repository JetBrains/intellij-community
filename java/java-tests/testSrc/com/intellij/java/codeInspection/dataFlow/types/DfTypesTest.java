// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DfTypesTest {
  @Test
  public void testDoubleRange() {
    DfType type = DfTypes.doubleValue(Math.nextUp(Double.NEGATIVE_INFINITY));
    assertEquals("-1.7976931348623157E308", type.toString());
    DfType t1 = type.fromRelation(RelationType.LE);
    assertEquals("double <= -1.7976931348623157E308 (or NaN)", t1.toString());
    DfType t2 = t1.tryNegate();
    assertNotNull(t2);
    assertEquals("double >= -1.7976931348623155E308 not NaN", t2.toString());
    DfType t3 = t2.join(type);
    assertEquals("double != -Infinity not NaN", t3.toString());

    DfType range02 = DfTypes.doubleRange(0, 2);
    assertEquals("double >= 0.0 && <= 2.0 not NaN", range02.toString());
    DfType exclude02 = range02.tryNegate();
    assertNotNull(exclude02);
    assertEquals("double < 0.0 || > 2.0 (or NaN)", exclude02.toString());
    DfType range13 = DfTypes.doubleRange(1, 3);
    assertEquals("double >= 1.0 && <= 3.0 not NaN", range13.toString());
    DfType exclude13 = range13.tryNegate();
    assertNotNull(exclude13);
    assertEquals("double < 1.0 || > 3.0 (or NaN)", exclude13.toString());
    DfType range12 = range02.meet(range13);
    assertEquals("double >= 1.0 && <= 2.0 not NaN", range12.toString());
    DfType range03 = range02.join(range13);
    assertEquals("double >= 0.0 && <= 3.0 not NaN", range03.toString());
    DfType exclude12 = exclude02.join(exclude13);
    assertEquals("double < 1.0 || > 2.0 (or NaN)", exclude12.toString());
    DfType exclude03 = exclude02.meet(exclude13);
    assertEquals("double < 0.0 || > 3.0 (or NaN)", exclude03.toString());
  }
}

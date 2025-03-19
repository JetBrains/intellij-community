// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiTypes;
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

    DfType withNaN = DfTypes.doubleRange(0.0, 1.0).tryNegate();
    assertNotNull(withNaN);
    assertEquals("double < 0.0 || > 1.0 (or NaN)", withNaN.toString());
    DfType withoutNaN = withNaN.meet(DfTypes.doubleRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    assertEquals("double < 0.0 || > 1.0 not NaN", withoutNaN.toString());

    assertEquals("double >= 3.0 && <= 4.0 not NaN", withNaN.meet(DfTypes.doubleRange(3.0, 4.0)).toString());
    assertEquals("double >= -4.0 && <= -3.0 not NaN", withNaN.meet(DfTypes.doubleRange(-4.0, -3.0)).toString());
    assertEquals("double > 1.0 && <= 1.5 not NaN", withNaN.meet(DfTypes.doubleRange(0.5, 1.5)).toString());
    assertEquals("double >= -1.5 && < 0.0 not NaN", withNaN.meet(DfTypes.doubleRange(-1.5, 0.5)).toString());
    assertEquals("double >= -4.0 && <= 4.0 not NaN", withNaN.meet(DfTypes.doubleRange(-4.0, 4.0)).toString());

    DfType lt1 = DfTypes.doubleRange(Double.NEGATIVE_INFINITY, 1.0);
    DfType gt2 = DfTypes.doubleRange(2.0, Double.POSITIVE_INFINITY);
    assertEquals("double <= 1.0 || >= 2.0 not NaN", lt1.join(gt2).toString());
    assertEquals("double <= 1.0 || >= 2.0 not NaN", gt2.join(lt1).toString());
  }
  
  @Test
  public void testDoubleCast() {
    assertEquals("double >= -9.223372036854776E18 && <= 9.223372036854776E18 not NaN", DfTypes.LONG.castTo(PsiTypes.doubleType()).toString());
    assertEquals("double >= -2.147483648E9 && <= 2.147483647E9 not NaN", DfTypes.INT.castTo(PsiTypes.doubleType()).toString());
    assertEquals("int", DfTypes.DOUBLE.castTo(PsiTypes.intType()).toString());
    assertEquals("int in {-32768..32767}", DfTypes.DOUBLE.castTo(PsiTypes.shortType()).toString());
    assertEquals("2147483647", ((DfDoubleType)DfTypes.doubleRange(1e10, 1e20)).castTo(PsiTypes.intType()).toString());
    assertEquals("-1", ((DfDoubleType)DfTypes.doubleRange(1e10, 1e20)).castTo(PsiTypes.shortType()).toString());
    assertEquals("long", ((DfDoubleType)DfTypes.LONG.castTo(PsiTypes.doubleType())).castTo(PsiTypes.longType()).toString());
    DfLongType range = (DfLongType)DfTypes.longRange(LongRangeSet.range(-1_234_567_890_123_458_234L, 1_234_567_890_123_458_234L));
    assertEquals("long in {-1234567890123458234..1234567890123458234}", range.toString());
    assertEquals("double >= -1.2345678901234583E18 && <= 1.2345678901234583E18 not NaN", range.castTo(PsiTypes.doubleType()).toString());
    assertEquals("long in {-1234567890123458304..1234567890123458304}", ((DfDoubleType)range.castTo(PsiTypes.doubleType())).castTo(
      PsiTypes.longType()).toString());
    assertEquals("long", ((DfFloatType)DfTypes.LONG.castTo(PsiTypes.floatType())).castTo(PsiTypes.longType()).toString());
    assertEquals("long <= 0 or >= 10", ((DfDoubleType)DfTypes.doubleRange(1.0, 10.0).tryNegate()).castTo(PsiTypes.longType()).toString());
  }
}

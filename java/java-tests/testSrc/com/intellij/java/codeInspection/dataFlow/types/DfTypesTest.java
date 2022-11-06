// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiType;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

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
    assertEquals("double >= -9.223372036854776E18 && <= 9.223372036854776E18 not NaN", DfTypes.LONG.castTo(PsiType.DOUBLE).toString());
    assertEquals("double >= -2.147483648E9 && <= 2.147483647E9 not NaN", DfTypes.INT.castTo(PsiType.DOUBLE).toString());
    assertEquals("int", DfTypes.DOUBLE.castTo(PsiType.INT).toString());
    assertEquals("int in {-32768..32767}", DfTypes.DOUBLE.castTo(PsiType.SHORT).toString());
    assertEquals("2147483647", ((DfDoubleType)DfTypes.doubleRange(1e10, 1e20)).castTo(PsiType.INT).toString());
    assertEquals("-1", ((DfDoubleType)DfTypes.doubleRange(1e10, 1e20)).castTo(PsiType.SHORT).toString());
    assertEquals("long", ((DfDoubleType)DfTypes.LONG.castTo(PsiType.DOUBLE)).castTo(PsiType.LONG).toString());
    DfLongType range = (DfLongType)DfTypes.longRange(LongRangeSet.range(-1_234_567_890_123_456_789L, 1_234_567_890_123_456_789L));
    assertEquals("long in {-1234567890123456789..1234567890123456789}", range.toString());
    assertEquals("double >= -1.23456789012345677E18 && <= 1.23456789012345677E18 not NaN", range.castTo(PsiType.DOUBLE).toString());
    assertEquals("long in {-1234567890123456768..1234567890123456768}", ((DfDoubleType)range.castTo(PsiType.DOUBLE)).castTo(PsiType.LONG).toString());
    assertEquals("long", ((DfFloatType)DfTypes.LONG.castTo(PsiType.FLOAT)).castTo(PsiType.LONG).toString());
    assertEquals("long <= 0 or >= 10", ((DfDoubleType)DfTypes.doubleRange(1.0, 10.0).tryNegate()).castTo(PsiType.LONG).toString());
  }

  @Test
  public void testBigInteger() {
    //point format
    DfType bigPoint = DfTypes.bigInteger(new BigInteger("123456789012345678901234567890"));
    assertEquals("1.23456789012345678901E29", bigPoint.toString());

    DfType point = DfTypes.bigInteger(new BigInteger("7890"));
    assertEquals("7890", point.toString());

    //point fromRelation
    DfType afterRelation = point.fromRelation(RelationType.LE);
    assertEquals("BigInt <= 7890", afterRelation.toString());
    DfType negativeRelation = afterRelation.tryNegate();
    assertNotNull(negativeRelation);
    assertEquals("BigInt >= 7891", negativeRelation.toString());

    afterRelation = point.fromRelation(RelationType.LT);
    assertEquals("BigInt <= 7889", afterRelation.toString());
    negativeRelation = afterRelation.tryNegate();
    assertNotNull(negativeRelation);
    assertEquals("BigInt >= 7890", negativeRelation.toString());

    afterRelation = point.fromRelation(RelationType.EQ);
    assertEquals("7890", afterRelation.toString());
    negativeRelation = afterRelation.tryNegate();
    assertNotNull(negativeRelation);
    assertEquals("BigInt != 7890", negativeRelation.toString());

    afterRelation = point.fromRelation(RelationType.NE);
    assertEquals("BigInt != 7890", afterRelation.toString());
    negativeRelation = afterRelation.tryNegate();
    assertNotNull(negativeRelation);
    assertEquals("7890", negativeRelation.toString());

    //point tryJoinExactly and join
    DfType joined = point.tryJoinExactly(point);
    assertEquals("7890", joined.toString());

    joined = point.tryJoinExactly(DfTypes.bigInteger(new BigInteger("7891")));
    assertNull(joined);
    joined = point.join(DfTypes.bigInteger(new BigInteger("7891")));
    assertNotNull(joined);
    assertEquals("BigInt >= 7890 && <= 7891", joined.toString());

    joined = point.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("7889"), new BigInteger("7892")));
    assertNotNull(joined);
    assertEquals("BigInt >= 7889 && <= 7892", joined.toString());

    joined = point.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("7880"), new BigInteger("7888")));
    assertNull(joined);
    joined = point.join(DfTypes.bigIntegerRange(new BigInteger("7870"), new BigInteger("7888")));
    assertNotNull(joined);
    assertEquals("BigInt >= 7870 && <= 7890", joined.toString());

    joined = point.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("7900"), new BigInteger("7901")));
    assertNull(joined);
    joined = point.join(DfTypes.bigIntegerRange(new BigInteger("7870"), new BigInteger("7880")));
    assertNotNull(joined);
    assertEquals("BigInt >= 7870 && <= 7890", joined.toString());

    //point meet
    DfType intersection = point.meet(point);
    assertEquals("7890", intersection.toString());

    intersection = point.meet(DfTypes.bigInteger(new BigInteger("7891")));
    assertEquals("BOTTOM", intersection.toString());

    intersection = point.meet(DfTypes.bigIntegerRange(new BigInteger("7889"), new BigInteger("7892")));
    assertEquals("7890", intersection.toString());

    intersection = point.meet(DfTypes.bigIntegerRange(new BigInteger("7989"), new BigInteger("7992")));
    assertEquals("BOTTOM", intersection.toString());

    intersection = point.meet(DfTypes.bigIntegerRange(new BigInteger("7989"), new BigInteger("7992")).tryNegate());
    assertEquals("7890", intersection.toString());

    //point meetRelationship
    DfType intersectionWithRelation = point.meetRelation(RelationType.GT, DfTypes.bigInteger(new BigInteger("7880")));
    assertEquals("7890", intersectionWithRelation.toString());

    //ranges format
    DfType range = DfTypes.bigIntegerRange(new BigInteger("1"), new BigInteger("10"));
    assertEquals("BigInt >= 1 && <= 10", range.toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate().toString());

    DfType bigRange =
      DfTypes.bigIntegerRange(new BigInteger("123456789012345678901234567890"), new BigInteger("223456789012345678901234567890"));
    assertEquals("BigInt > 1.23456789012345678901E29 && <= 2.23456789012345678901E29", bigRange.toString());
    assertEquals("BigInt <= 1.23456789012345678901E29 || > 2.23456789012345678901E29", bigRange.tryNegate().toString());

    //range isSuperType
    assertTrue(range.isSuperType(DfTypes.bigInteger(new BigInteger("2"))));
    assertFalse(range.isSuperType(DfTypes.bigInteger(new BigInteger("11"))));
    assertFalse(range.isSuperType(DfTypes.bigInteger(new BigInteger("0"))));
    assertTrue(range.isSuperType(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("9"))));
    assertFalse(range.isSuperType(DfTypes.bigIntegerRange(new BigInteger("0"), new BigInteger("9"))));
    assertFalse(range.isSuperType(DfTypes.bigIntegerRange(new BigInteger("-1"), new BigInteger("0"))));

    assertFalse(range.tryNegate().isSuperType(DfTypes.bigInteger(new BigInteger("2"))));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigInteger(new BigInteger("10"))));
    assertTrue(range.tryNegate().isSuperType(DfTypes.bigInteger(new BigInteger("11"))));
    assertTrue(range.tryNegate().isSuperType(DfTypes.bigInteger(new BigInteger("0"))));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("9"))));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("0"), new BigInteger("9"))));
    assertTrue(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("-1"), new BigInteger("0"))));

    assertFalse(range.tryNegate().isSuperType(DfTypes.bigInteger(new BigInteger("2")).tryNegate()));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigInteger(new BigInteger("11")).tryNegate()));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("9")).tryNegate()));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("0"), new BigInteger("9")).tryNegate()));
    assertFalse(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("-1"), new BigInteger("0")).tryNegate()));
    assertTrue(range.tryNegate().isSuperType(DfTypes.bigIntegerRange(new BigInteger("-1"), new BigInteger("10")).tryNegate()));

    assertTrue(DfTypes.bigIntegerInfinite().isSuperType(DfTypes.bigInteger(new BigInteger("11")).tryNegate()));
    assertFalse(range.isSuperType(DfTypes.bigInteger(new BigInteger("2")).tryNegate()));
    assertFalse(range.isSuperType(DfTypes.bigInteger(new BigInteger("11")).tryNegate()));
    assertFalse(range.isSuperType(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("9")).tryNegate()));
    assertFalse(range.isSuperType(DfTypes.bigIntegerRange(new BigInteger("0"), new BigInteger("9")).tryNegate()));
    assertFalse(range.isSuperType(DfTypes.bigIntegerRange(new BigInteger("-1"), new BigInteger("0")).tryNegate()));

    //range fromRelation
    assertEquals("BigInt <= 9", range.fromRelation(RelationType.LT).toString());
    assertEquals("BigInt <= 10", range.fromRelation(RelationType.LE).toString());
    assertEquals("BigInt >= 1 && <= 10", range.fromRelation(RelationType.EQ).toString());
    assertEquals("BigInt", range.fromRelation(RelationType.NE).toString());
    assertEquals("BigInt >= 1", range.fromRelation(RelationType.GE).toString());
    assertEquals("BigInt >= 2", range.fromRelation(RelationType.GT).toString());

    assertEquals("BigInt >= 10", range.fromRelation(RelationType.LT).tryNegate().toString());
    assertEquals("BigInt >= 11", range.fromRelation(RelationType.LE).tryNegate().toString());
    assertEquals("BigInt <= 0 || >= 11", range.fromRelation(RelationType.EQ).tryNegate().toString());
    assertEquals("BOTTOM", range.fromRelation(RelationType.NE).tryNegate().toString());
    assertEquals("BigInt <= 0", range.fromRelation(RelationType.GE).tryNegate().toString());
    assertEquals("BigInt <= 1", range.fromRelation(RelationType.GT).tryNegate().toString());

    assertEquals("BOTTOM", DfTypes.bigIntegerInfinite().tryNegate().fromRelation(RelationType.NE).toString());
    assertEquals("BigInt", DfTypes.bigIntegerInfinite().fromRelation(RelationType.NE).toString());

    assertEquals("BigInt", range.tryNegate().fromRelation(RelationType.LT).toString());
    assertEquals("BigInt", range.tryNegate().fromRelation(RelationType.LE).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate().fromRelation(RelationType.EQ).toString());
    assertEquals("BigInt", range.tryNegate().fromRelation(RelationType.NE).toString());
    assertEquals("BigInt", range.tryNegate().fromRelation(RelationType.GE).toString());
    assertEquals("BigInt", range.tryNegate().fromRelation(RelationType.GT).toString());

    //range join
    assertEquals("BigInt >= 1 && <= 10", range.join(DfTypes.bigInteger(new BigInteger("2"))).toString());
    assertEquals("BigInt >= 0 && <= 10", range.join(DfTypes.bigInteger(new BigInteger("0"))).toString());
    assertEquals("BigInt >= 1 && <= 11", range.join(DfTypes.bigInteger(new BigInteger("11"))).toString());

    assertEquals("BigInt", range.tryNegate().join(DfTypes.bigInteger(new BigInteger("2"))).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate().join(DfTypes.bigInteger(new BigInteger("-1"))).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate().join(DfTypes.bigInteger(new BigInteger("12"))).toString());

    assertEquals("BigInt",
                 DfTypes.bigInteger(new BigInteger("1")).fromRelation(RelationType.LT)
                   .join(DfTypes.bigInteger(new BigInteger("11")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt",
                 DfTypes.bigInteger(new BigInteger("1")).fromRelation(RelationType.GT)
                   .join(DfTypes.bigInteger(new BigInteger("11")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt >= 2",
                 DfTypes.bigInteger(new BigInteger("1")).fromRelation(RelationType.GT)
                   .join(DfTypes.bigInteger(new BigInteger("11")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt <= 10",
                 DfTypes.bigInteger(new BigInteger("1")).fromRelation(RelationType.LT)
                   .join(DfTypes.bigInteger(new BigInteger("11")).fromRelation(RelationType.LT)).toString());

    assertEquals("BigInt >= 1",
                 range.join(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt <= 12",
                 range.join(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt >= -9",
                 range.join(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt <= 10",
                 range.join(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt >= 1",
                 range.join(DfTypes.bigInteger(new BigInteger("5")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt <= 10",
                 range.join(DfTypes.bigInteger(new BigInteger("5")).fromRelation(RelationType.LT)).toString());

    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().join(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt",
                 range.tryNegate().join(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt",
                 range.tryNegate().join(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().join(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.LT)).toString());

    assertEquals("BigInt >= 1 && <= 10", range.join(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8"))).toString());
    assertEquals("BigInt >= -8 && <= 10", range.join(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2"))).toString());
    assertEquals("BigInt >= 1 && <= 20", range.join(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20"))).toString());
    assertEquals("BigInt >= -3 && <= 20", range.join(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20"))).toString());

    assertEquals("BigInt", range.tryNegate().join(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8"))).toString());
    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().join(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2"))).toString());
    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().join(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20"))).toString());
    assertEquals("BigInt", range.tryNegate().join(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20"))).toString());

    assertEquals("BigInt", range.join(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8")).tryNegate()).toString());
    assertEquals("BigInt <= -9 || >= -1",
                 range.join(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2")).tryNegate()).toString());
    assertEquals("BigInt <= 10 || >= 21",
                 range.join(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20")).tryNegate()).toString());
    assertEquals("BigInt", range.join(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20")).tryNegate()).toString());

    assertEquals("BigInt <= 1 || >= 9", range.tryNegate()
      .join(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8")).tryNegate()).toString());
    assertEquals("BigInt", range.tryNegate()
      .join(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2")).tryNegate()).toString());
    assertEquals("BigInt", range.tryNegate()
      .join(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20")).tryNegate()).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate()
      .join(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20")).tryNegate()).toString());

    //range tryJoinExactly
    assertNull("BigInt >= 1",
               range.tryJoinExactly(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.GT)));
    assertEquals("BigInt <= 12",
                 range.tryJoinExactly(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt >= -9",
                 range.tryJoinExactly(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.GT)).toString());
    assertNull("BigInt <= 10",
               range.tryJoinExactly(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.LT)));

    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().tryJoinExactly(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt",
                 range.tryNegate().tryJoinExactly(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt",
                 range.tryNegate().tryJoinExactly(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().tryJoinExactly(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.LT)).toString());

    assertEquals("BigInt >= 1 && <= 10",
                 range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8"))).toString());
    assertNull(range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2"))));
    assertEquals("BigInt >= 1 && <= 20",
                 range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20"))).toString());
    assertEquals("BigInt >= -3 && <= 20",
                 range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20"))).toString());

    assertNull("BigInt", range.tryNegate().tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8"))));
    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2"))).toString());
    assertEquals("BigInt <= 0 || >= 11",
                 range.tryNegate().tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20"))).toString());
    assertEquals("BigInt",
                 range.tryNegate().tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20"))).toString());

    assertEquals("BigInt", range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8")).tryNegate()).toString());
    assertEquals("BigInt <= -9 || >= -1",
                 range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2")).tryNegate()).toString());
    assertEquals("BigInt <= 10 || >= 21",
                 range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20")).tryNegate()).toString());
    assertNull(range.tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20")).tryNegate()));

    assertEquals("BigInt <= 1 || >= 9", range.tryNegate()
      .tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("8")).tryNegate()).toString());
    assertEquals("BigInt", range.tryNegate()
      .tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-8"), new BigInteger("-2")).tryNegate()).toString());
    assertEquals("BigInt", range.tryNegate()
      .tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("11"), new BigInteger("20")).tryNegate()).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate()
      .tryJoinExactly(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20")).tryNegate()).toString());

    //range meet
    assertEquals("BOTTOM", range.meet(DfTypes.bigInteger(new BigInteger("-10"))).toString());
    assertEquals("5", range.meet(DfTypes.bigInteger(new BigInteger("5"))).toString());
    assertEquals("BOTTOM", range.meet(DfTypes.bigInteger(new BigInteger("20"))).toString());

    assertEquals("-10", range.tryNegate().meet(DfTypes.bigInteger(new BigInteger("-10"))).toString());
    assertEquals("BOTTOM", range.tryNegate().meet(DfTypes.bigInteger(new BigInteger("5"))).toString());
    assertEquals("20", range.tryNegate().meet(DfTypes.bigInteger(new BigInteger("20"))).toString());

    assertEquals("BigInt >= 1 && <= 10", range.meet(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20"))).toString());
    assertEquals("BigInt >= 2 && <= 5", range.meet(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("5"))).toString());
    assertEquals("BOTTOM", range.meet(DfTypes.bigIntegerRange(new BigInteger("20"), new BigInteger("50"))).toString());

    assertEquals("BigInt <= -4 || >= 21", range.tryNegate().meet(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20")).tryNegate()).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate().meet(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("5")).tryNegate()).toString());
    assertEquals("BigInt <= 0 || >= 11", range.tryNegate().meet(DfTypes.bigIntegerRange(new BigInteger("20"), new BigInteger("50")).tryNegate()).toString());

    assertEquals("BOTTOM", range.meet(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20")).tryNegate()).toString());
    assertEquals("BigInt >= 1 && <= 10", range.meet(DfTypes.bigIntegerRange(new BigInteger("2"), new BigInteger("5")).tryNegate()).toString());
    assertEquals("BigInt >= 1 && <= 10", range.meet(DfTypes.bigIntegerRange(new BigInteger("20"), new BigInteger("50")).tryNegate()).toString());

    assertEquals("BigInt >= -3 && <= 20", range.tryNegate().meet(DfTypes.bigIntegerRange(new BigInteger("-3"), new BigInteger("20"))).toString());

    assertEquals("BOTTOM",
                 range.meet(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt >= 1 && <= 10",
                 range.meet(DfTypes.bigInteger(new BigInteger("13")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt >= 1 && <= 10",
                 range.meet(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.GT)).toString());
    assertEquals("BigInt >= 1 && <= 4",
                 range.meet(DfTypes.bigInteger(new BigInteger("5")).fromRelation(RelationType.LT)).toString());
    assertEquals("BigInt >= 6 && <= 10",
                 range.meet(DfTypes.bigInteger(new BigInteger("5")).fromRelation(RelationType.GT)).toString());
    assertEquals("BOTTOM",
                 range.meet(DfTypes.bigInteger(new BigInteger("-10")).fromRelation(RelationType.LT)).toString());

  }
}

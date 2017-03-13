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
package com.intellij.codeInspection.dataFlow.rangeSet;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import org.junit.Test;

import java.util.Random;

import static com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet.*;
import static org.junit.Assert.*;

/**
 * @author Tagir Valeev
 */
public class LongRangeSetTest {
  @Test
  public void testToString() {
    assertEquals("{}", LongRangeSet.empty().toString());
    assertEquals("{10}", point(10).toString());
    assertEquals("{10}", range(10, 10).toString());
    assertEquals("{10, 11}", range(10, 11).toString());
    assertEquals("{10..100}", range(10, 100).toString());
  }

  @Test
  public void testFromType() {
    assertNull(LongRangeSet.fromType(PsiType.FLOAT));
    assertNull(LongRangeSet.fromType(PsiType.NULL));
    assertEquals("{-128..127}", LongRangeSet.fromType(PsiType.BYTE).toString());
    assertEquals("{0..65535}", LongRangeSet.fromType(PsiType.CHAR).toString());
    assertEquals("{-32768..32767}", LongRangeSet.fromType(PsiType.SHORT).toString());
    assertEquals("{-2147483648..2147483647}", LongRangeSet.fromType(PsiType.INT).toString());
    assertEquals("{0..2147483647}", LongRangeSet.indexRange().toString());
    assertEquals("{-9223372036854775808..9223372036854775807}", LongRangeSet.fromType(PsiType.LONG).toString());
  }

  @Test
  public void testEquals() {
    assertEquals(LongRangeSet.empty(), LongRangeSet.empty());
    assertEquals(point(10), point(10));
    assertNotEquals(point(10), point(11));
    assertEquals(point(10), range(10, 10));
    assertNotEquals(point(10), range(10, 11));
    assertEquals(range(10, 11), range(10, 11));
    assertNotEquals(range(10, 11), range(10, 12));
  }

  @Test
  public void testDiff() {
    assertEquals(LongRangeSet.empty(), LongRangeSet.empty().subtract(point(10)));
    assertEquals(point(10), point(10).subtract(LongRangeSet.empty()));
    assertEquals(point(10), point(10).subtract(point(11)));
    assertEquals(LongRangeSet.empty(), point(10).subtract(point(10)));
    assertEquals(point(10), point(10).subtract(range(15, 20)));
    assertEquals(point(10), point(10).subtract(range(-10, -5)));
    assertTrue(point(10).subtract(range(10, 20)).isEmpty());
    assertTrue(point(10).subtract(range(-10, 20)).isEmpty());
    assertTrue(point(10).subtract(range(-10, 10)).isEmpty());

    assertEquals("{0..20}", range(0, 20).lt(30).toString());
    assertEquals("{0..19}", range(0, 20).lt(20).toString());
    assertEquals("{0..18}", range(0, 20).lt(19).toString());
    assertEquals("{0}", range(0, 20).lt(1).toString());
    assertTrue(range(0, 20).lt(0).isEmpty());

    LongRangeSet fullRange = range(Long.MIN_VALUE, Long.MAX_VALUE);
    assertEquals("{-9223372036854775808}", fullRange.le(Long.MIN_VALUE).toString());
    assertEquals(fullRange, fullRange.le(Long.MAX_VALUE));
    assertEquals("{9223372036854775807}", fullRange.ge(Long.MAX_VALUE).toString());
    assertEquals(fullRange, fullRange.ge(Long.MIN_VALUE));
    assertTrue(fullRange.gt(Long.MAX_VALUE).isEmpty());
    assertEquals(LongRangeSet.indexRange(), LongRangeSet.fromType(PsiType.INT).gt(-1));
    assertTrue(fullRange.subtract(fullRange).isEmpty());

    assertEquals(point(10), fullRange.eq(10));
    assertTrue(range(30, 50).eq(10).isEmpty());
  }

  @Test
  public void testSets() {
    assertEquals("{0..9, 11..20}", range(0, 20).ne(10).toString());
    assertEquals("{0, 20}", range(0, 20).subtract(range(1, 19)).toString());
    assertEquals("{0, 1, 19, 20}", range(0, 20).subtract(range(2, 18)).toString());

    assertEquals("{0..9, 12..20}", range(0, 20).ne(10).ne(11).toString());
    assertEquals("{0..9, 12..14, 16..20}", range(0, 20).ne(10).ne(11).ne(15).toString());
    assertEquals("{0, 4..20}", range(0, 20).ne(3).ne(2).ne(1).toString());
    assertEquals("{4..20}", range(0, 20).ne(3).ne(2).ne(1).ne(0).toString());

    assertEquals("{0..2, 5..15, 19, 20}",
                 range(0, 20).subtract(range(3, 18).subtract(range(5, 15))).toString());

    LongRangeSet first = fromType(PsiType.CHAR).ne(45);
    LongRangeSet second = fromType(PsiType.CHAR).ne(32).ne(40).ne(44).ne(45).ne(46).ne(58).ne(59).ne(61);
    assertEquals("{0..44, 46..65535}", first.toString());
    assertEquals("{0..31, 33..39, 41..43, 47..57, 60, 62..65535}", second.toString());
    assertEquals("{32, 40, 44, 46, 58, 59, 61}", first.subtract(second).toString());
  }

  @Test
  public void testHash() {
    HashMap<LongRangeSet, String> map = new HashMap<>();
    map.put(LongRangeSet.empty(), "empty");
    map.put(point(10), "10");
    map.put(range(10, 10), "10-10");
    map.put(range(10, 11), "10-11");
    map.put(range(10, 12), "10-12");
    LongRangeSet longNotChar = LongRangeSet.fromType(PsiType.LONG).subtract(LongRangeSet.fromType(PsiType.CHAR));
    map.put(longNotChar, "longNotChar");

    assertEquals("empty", map.get(LongRangeSet.empty()));
    assertEquals("10-10", map.get(point(10)));
    assertEquals("10-11", map.get(range(10, 11)));
    assertEquals("10-12", map.get(range(10, 12)));
    assertNull(map.get(range(11, 11)));
    assertEquals("longNotChar", map.get(LongRangeSet.fromType(PsiType.LONG).subtract(LongRangeSet.fromType(PsiType.CHAR))));
  }

  @Test
  public void testIntersects() {
    assertFalse(LongRangeSet.empty().intersects(LongRangeSet.fromType(PsiType.LONG)));
    assertTrue(point(Long.MIN_VALUE).intersects(LongRangeSet.fromType(PsiType.LONG)));
    assertFalse(point(10).intersects(point(11)));
    assertTrue(point(10).intersects(point(10)));

    assertTrue(range(10, 100).intersects(point(10)));
    assertTrue(range(10, 100).intersects(point(100)));
    assertFalse(range(10, 100).intersects(point(101)));
    assertFalse(range(10, 100).intersects(point(9)));

    LongRangeSet range1020 = range(10, 20);
    assertTrue(range1020.intersects(range1020));
    assertTrue(range1020.intersects(range(10, 30)));
    assertTrue(range1020.intersects(range(20, 30)));
    assertTrue(range1020.intersects(range(0, 30)));
    assertTrue(range1020.intersects(range(0, 10)));
    assertTrue(range1020.intersects(range(0, 20)));

    assertFalse(range1020.intersects(range(0, 9)));
    assertFalse(range1020.intersects(range(21, 30)));

    LongRangeSet rangeSet = range1020.subtract(range(12, 13)).subtract(range(17, 18));
    assertFalse(rangeSet.intersects(point(12)));
    assertFalse(point(12).intersects(rangeSet));
    assertFalse(rangeSet.intersects(LongRangeSet.empty()));
    assertFalse(rangeSet.intersects(range(12, 13)));
    assertFalse(range(12, 13).intersects(rangeSet));
    assertFalse(rangeSet.intersects(range(0, 9)));
    assertFalse(rangeSet.intersects(range(21, 30)));
    assertTrue(rangeSet.intersects(rangeSet));
    assertTrue(rangeSet.intersects(range1020));
    assertTrue(rangeSet.intersects(point(11)));

    LongRangeSet rangeSet2 = range1020.subtract(rangeSet);
    assertEquals("{12, 13, 17, 18}", rangeSet2.toString());
    assertFalse(rangeSet.intersects(rangeSet2));
  }

  @Test
  public void testIntersect() {
    assertEquals("{0..100}", range(0, 100).intersect(range(0, 100)).toString());
    assertEquals("{100}", range(0, 100).intersect(range(100, 200)).toString());
    assertTrue(range(0, 100).intersect(range(101, 200)).isEmpty());
    assertTrue(point(100).intersect(point(200)).isEmpty());
    assertFalse(point(100).intersect(range(99, 101)).isEmpty());

    LongRangeSet rangeSet = range(-1000, 1000).subtract(range(100, 500)).subtract(range(-500, -100));
    assertEquals("{-1000..-501, -99..99, 501..1000}", rangeSet.toString());
    assertEquals(point(99), rangeSet.intersect(point(99)));
    assertTrue(rangeSet.intersect(point(100)).isEmpty());
    assertEquals("{0..99, 501..1000}", rangeSet.intersect(LongRangeSet.indexRange()).toString());
  }

  @Test
  public void testIntersectSubtractRandomized() {
    Random r = new Random(1);
    LongRangeSet[] data = r.ints(1000, 0, 1000)
      .mapToObj(x -> range(x, x + r.nextInt((x % 20) * 100 + 1))).toArray(LongRangeSet[]::new);
    for (int i = 0; i < 2000; i++) {
      int idx = r.nextInt(data.length);
      LongRangeSet left = data[idx];
      LongRangeSet right = data[r.nextInt(data.length)];
      LongRangeSet lDiff = left.subtract(right);
      LongRangeSet rDiff = right.subtract(left);
      LongRangeSet intersection = left.intersect(right);
      String message = left + " & " + right + " = " + intersection;
      assertEquals(message, intersection, right.intersect(left));
      if (!intersection.isEmpty()) {
        assertTrue(message, intersection.min() >= Math.max(left.min(), right.min()));
        assertTrue(message, intersection.max() <= Math.min(left.max(), right.max()));
      }
      assertEquals(message, intersection, right.subtract(LongRangeSet.fromType(PsiType.LONG).subtract(left)));
      assertEquals(message, intersection, left.subtract(LongRangeSet.fromType(PsiType.LONG).subtract(right)));
      switch (r.nextInt(3)) {
        case 0:
          data[idx] = lDiff;
          break;
        case 1:
          data[idx] = rDiff;
          break;
        case 2:
          data[idx] = intersection;
          break;
      }
    }
  }

  @Test
  public void testFromConstant() {
    assertEquals("{0}", LongRangeSet.fromConstant(0).toString());
    assertEquals("{0}", LongRangeSet.fromConstant(0L).toString());
    assertEquals("{1}", LongRangeSet.fromConstant((byte)1).toString());
    assertEquals("{97}", LongRangeSet.fromConstant('a').toString());
    assertNull(LongRangeSet.fromConstant(null));
    assertNull(LongRangeSet.fromConstant(1.0));
  }

  @Test
  public void testFromRelation() {
    assertEquals(range(101, Long.MAX_VALUE), range(100, 200).fromRelation(JavaTokenType.GT));
    assertEquals(range(100, Long.MAX_VALUE), range(100, 200).fromRelation(JavaTokenType.GE));
    assertEquals(range(Long.MIN_VALUE, 199), range(100, 200).fromRelation(JavaTokenType.LT));
    assertEquals(range(Long.MIN_VALUE, 200), range(100, 200).fromRelation(JavaTokenType.LE));
    assertEquals(range(100, 200), range(100, 200).fromRelation(JavaTokenType.EQEQ));
    assertNull(range(100, 200).fromRelation(JavaTokenType.EQ));
    assertEquals(fromType(PsiType.LONG), range(100, 200).fromRelation(JavaTokenType.NE));
    assertEquals("{-9223372036854775808..99, 101..9223372036854775807}", point(100).fromRelation(JavaTokenType.NE).toString());
  }
}
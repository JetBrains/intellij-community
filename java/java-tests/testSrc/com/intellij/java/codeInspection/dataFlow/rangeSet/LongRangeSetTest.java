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
package com.intellij.java.codeInspection.dataFlow.rangeSet;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import org.junit.Test;

import java.util.Random;
import java.util.function.Function;

import static com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet.*;
import static org.junit.Assert.*;

/**
 * @author Tagir Valeev
 */
public class LongRangeSetTest {
  @Test
  public void testToString() {
    assertEquals("{}", empty().toString());
    assertEquals("{10}", point(10).toString());
    assertEquals("{10}", range(10, 10).toString());
    assertEquals("{10, 11}", range(10, 11).toString());
    assertEquals("{10..100}", range(10, 100).toString());
  }

  @Test
  public void testFromType() {
    assertNull(fromType(PsiType.FLOAT));
    assertNull(fromType(PsiType.NULL));
    assertEquals("{-128..127}", fromType(PsiType.BYTE).toString());
    assertEquals("{0..65535}", fromType(PsiType.CHAR).toString());
    assertEquals("{-32768..32767}", fromType(PsiType.SHORT).toString());
    assertEquals("{-2147483648..2147483647}", fromType(PsiType.INT).toString());
    assertEquals("{0..2147483647}", indexRange().toString());
    assertEquals("{-9223372036854775808..9223372036854775807}", fromType(PsiType.LONG).toString());
  }

  @Test
  public void testEquals() {
    assertEquals(empty(), empty());
    assertEquals(point(10), point(10));
    assertNotEquals(point(10), point(11));
    assertEquals(point(10), range(10, 10));
    assertNotEquals(point(10), range(10, 11));
    assertEquals(range(10, 11), range(10, 11));
    assertNotEquals(range(10, 11), range(10, 12));
  }

  @Test
  public void testDiff() {
    assertEquals(empty(), empty().subtract(point(10)));
    assertEquals(point(10), point(10).subtract(empty()));
    assertEquals(point(10), point(10).subtract(point(11)));
    assertEquals(empty(), point(10).subtract(point(10)));
    assertEquals(point(10), point(10).subtract(range(15, 20)));
    assertEquals(point(10), point(10).subtract(range(-10, -5)));
    assertTrue(point(10).subtract(range(10, 20)).isEmpty());
    assertTrue(point(10).subtract(range(-10, 20)).isEmpty());
    assertTrue(point(10).subtract(range(-10, 10)).isEmpty());

    assertEquals("{0..20}", range(0, 20).subtract(range(30, Long.MAX_VALUE)).toString());
    assertEquals("{0..19}", range(0, 20).subtract(range(20, Long.MAX_VALUE)).toString());
    assertEquals("{0..18}", range(0, 20).subtract(range(19, Long.MAX_VALUE)).toString());
    assertEquals("{0}", range(0, 20).subtract(range(1, Long.MAX_VALUE)).toString());
    assertTrue(range(0, 20).subtract(range(0, Long.MAX_VALUE)).isEmpty());

    assertEquals("{-9223372036854775808}", all().subtract(range(Long.MIN_VALUE + 1, Long.MAX_VALUE)).toString());
    assertEquals("{9223372036854775807}", all().subtract(range(Long.MIN_VALUE, Long.MAX_VALUE - 1)).toString());
    assertTrue(all().subtract(range(Long.MIN_VALUE, Long.MAX_VALUE)).isEmpty());
    assertEquals(indexRange(), fromType(PsiType.INT).subtract(range(Long.MIN_VALUE, (long)-1)));
    assertTrue(all().subtract(all()).isEmpty());
  }

  @Test
  public void testSets() {
    assertEquals("{0..9, 11..20}", range(0, 20).without(10).toString());
    assertEquals("{0, 20}", range(0, 20).subtract(range(1, 19)).toString());
    assertEquals("{0, 1, 19, 20}", range(0, 20).subtract(range(2, 18)).toString());

    assertEquals("{0..9, 12..20}", range(0, 20).without(10).without(11).toString());
    assertEquals("{0..9, 12..14, 16..20}", range(0, 20).without(10).without(11).without(15).toString());
    assertEquals("{0, 4..20}", range(0, 20).without(3).without(2).without(1).toString());
    assertEquals("{4..20}", range(0, 20).without(3).without(2).without(1).without(0).toString());

    assertEquals("{0..2, 5..15, 19, 20}",
                 range(0, 20).subtract(range(3, 18).subtract(range(5, 15))).toString());

    LongRangeSet first = fromType(PsiType.CHAR).without(45);
    LongRangeSet second =
      fromType(PsiType.CHAR).without(32).without(40).without(44).without(45).without(46).without(58).without(59).without(61);
    assertEquals("{0..44, 46..65535}", first.toString());
    assertEquals("{0..31, 33..39, 41..43, 47..57, 60, 62..65535}", second.toString());
    assertEquals("{32, 40, 44, 46, 58, 59, 61}", first.subtract(second).toString());
  }

  @Test
  public void testHash() {
    HashMap<LongRangeSet, String> map = new HashMap<>();
    map.put(empty(), "empty");
    map.put(point(10), "10");
    map.put(range(10, 10), "10-10");
    map.put(range(10, 11), "10-11");
    map.put(range(10, 12), "10-12");
    LongRangeSet longNotChar = fromType(PsiType.LONG).subtract(fromType(PsiType.CHAR));
    map.put(longNotChar, "longNotChar");

    assertEquals("empty", map.get(empty()));
    assertEquals("10-10", map.get(point(10)));
    assertEquals("10-11", map.get(range(10, 11)));
    assertEquals("10-12", map.get(range(10, 12)));
    assertNull(map.get(range(11, 11)));
    assertEquals("longNotChar", map.get(fromType(PsiType.LONG).subtract(fromType(PsiType.CHAR))));
  }

  @Test
  public void testIntersects() {
    assertFalse(empty().intersects(fromType(PsiType.LONG)));
    assertTrue(point(Long.MIN_VALUE).intersects(fromType(PsiType.LONG)));
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
    assertFalse(rangeSet.intersects(empty()));
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
    assertEquals("{0..99, 501..1000}", rangeSet.intersect(indexRange()).toString());
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
      assertEquals(message, intersection, right.subtract(fromType(PsiType.LONG).subtract(left)));
      assertEquals(message, intersection, left.subtract(fromType(PsiType.LONG).subtract(right)));
      intersection.stream().limit(1000).forEach(e -> {
        assertTrue(left.contains(e));
        assertTrue(right.contains(e));
      });
      lDiff.stream().limit(1000).forEach(e -> {
        assertTrue(left.contains(e));
        assertFalse(right.contains(e));
      });
      rDiff.stream().limit(1000).forEach(e -> {
        assertFalse(left.contains(e));
        assertTrue(right.contains(e));
      });
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
    assertEquals("{0}", fromConstant(0).toString());
    assertEquals("{0}", fromConstant(0L).toString());
    assertEquals("{1}", fromConstant((byte)1).toString());
    assertEquals("{97}", fromConstant('a').toString());
    assertNull(fromConstant(null));
    assertNull(fromConstant(1.0));
  }

  @Test
  public void testFromRelation() {
    assertEquals(range(101, Long.MAX_VALUE), range(100, 200).fromRelation(RelationType.GT));
    assertEquals(range(100, Long.MAX_VALUE), range(100, 200).fromRelation(RelationType.GE));
    assertEquals(range(Long.MIN_VALUE, 199), range(100, 200).fromRelation(RelationType.LT));
    assertEquals(range(Long.MIN_VALUE, 200), range(100, 200).fromRelation(RelationType.LE));
    assertEquals(range(100, 200), range(100, 200).fromRelation(RelationType.EQ));
    assertNull(range(100, 200).fromRelation(RelationType.IS));
    assertEquals(fromType(PsiType.LONG), range(100, 200).fromRelation(RelationType.NE));
    assertEquals("{-9223372036854775808..99, 101..9223372036854775807}", point(100).fromRelation(RelationType.NE).toString());
  }

  @Test
  public void testAbs() {
    assertTrue(empty().abs(true).isEmpty());
    assertEquals(point(Long.MAX_VALUE), point(Long.MIN_VALUE + 1).abs(true));
    assertEquals(point(Long.MIN_VALUE), point(Long.MIN_VALUE).abs(true));
    assertEquals(point(Integer.MIN_VALUE), point(Integer.MIN_VALUE).abs(false));
    assertEquals(point(Integer.MAX_VALUE + 1L), point(Integer.MIN_VALUE).abs(true));
    assertEquals(range(100, 200), range(100, 200).abs(true));
    assertEquals(range(0, 200), range(-1, 200).abs(true));
    assertEquals(range(0, 200), range(-200, 200).abs(false));
    assertEquals(range(0, 201), range(-201, 200).abs(false));
    assertEquals(range(0, Long.MAX_VALUE).union(point(Long.MIN_VALUE)), all().abs(true));
    assertEquals(range(100, Integer.MAX_VALUE).union(point(Integer.MIN_VALUE)), range(Integer.MIN_VALUE, -100).abs(false));
    assertEquals(range(100, Integer.MAX_VALUE + 1L), range(Integer.MIN_VALUE, -100).abs(true));
    LongRangeSet set = range(-900, 1000).subtract(range(-800, -600)).subtract(range(-300, 100)).subtract(range(500, 700));
    assertEquals("{-900..-801, -599..-301, 101..499, 701..1000}", set.toString());
    assertEquals("{101..599, 701..1000}", set.abs(false).toString());
  }

  @Test
  public void testBitwiseAnd() {
    assertTrue(empty().bitwiseAnd(all()).isEmpty());
    assertTrue(all().bitwiseAnd(empty()).isEmpty());
    assertEquals(all(), all().bitwiseAnd(all()));
    assertEquals("{0, 16}", all().bitwiseAnd(point(16)).toString());
    assertEquals("{0, 1}", all().bitwiseAnd(point(1)).toString());
    assertEquals(range(0, 24), all().bitwiseAnd(point(24)));
    assertEquals(range(0, 31), all().bitwiseAnd(point(25)));
    assertEquals(range(0, 15), all().bitwiseAnd(range(10, 15)));
    assertEquals(range(0, 31), all().bitwiseAnd(range(16, 24)));

    checkBitwiseAnd(range(0, 3), range(4, 7), "{0..3}");
    checkBitwiseAnd(range(3, 4), range(3, 4), "{0..7}"); // 0,3,4,7 actually
    checkBitwiseAnd(range(-20, 20), point(8), "{0, 8}");
    checkBitwiseAnd(point(3).union(point(5)), point(3).union(point(5)), "{1, 3, 5}");
    checkBitwiseAnd(range(-10, 10), range(-20, 5), "{-32..15}");
    checkBitwiseAnd(range(-30, -20).union(range(20, 33)), point(-10).union(point(10)), "{-32..-26, 0..62}");
  }

  void checkBitwiseAnd(LongRangeSet range1, LongRangeSet range2, String expected) {
    LongRangeSet result = range1.bitwiseAnd(range2);
    assertEquals(expected, result.toString());
    assertTrue(
      range1.stream().mapToObj(l1 -> range2.stream().map(l2 -> l1 & l2)).flatMapToLong(Function.identity()).allMatch(result::contains));
  }
}
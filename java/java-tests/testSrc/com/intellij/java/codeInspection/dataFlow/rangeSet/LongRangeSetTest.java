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
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet.*;
import static org.junit.Assert.*;

public class LongRangeSetTest {
  @NotNull
  private static LongRangeSet fromTypeStrict(PsiType type) {
    LongRangeSet range = fromType(type);
    assertNotNull(range);
    return range;
  }

  @NotNull
  private static LongRangeSet fromConstantStrict(Object constant) {
    LongRangeSet range = fromConstant(constant);
    assertNotNull(range);
    return range;
  }

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
    assertEquals("{-128..127}", fromTypeStrict(PsiType.BYTE).toString());
    assertEquals("{0..65535}", fromTypeStrict(PsiType.CHAR).toString());
    assertEquals("{-32768..32767}", fromTypeStrict(PsiType.SHORT).toString());
    assertEquals("{Integer.MIN_VALUE..Integer.MAX_VALUE}", fromTypeStrict(PsiType.INT).toString());
    assertEquals("{0..Integer.MAX_VALUE}", indexRange().toString());
    assertEquals("{Long.MIN_VALUE..Long.MAX_VALUE}", fromTypeStrict(PsiType.LONG).toString());
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

    assertEquals("{Long.MIN_VALUE}", all().subtract(range(Long.MIN_VALUE + 1, Long.MAX_VALUE)).toString());
    assertEquals("{Long.MAX_VALUE}", all().subtract(range(Long.MIN_VALUE, Long.MAX_VALUE - 1)).toString());
    assertTrue(all().subtract(range(Long.MIN_VALUE, Long.MAX_VALUE)).isEmpty());
    assertEquals(indexRange(), fromTypeStrict(PsiType.INT).subtract(range(Long.MIN_VALUE, (long)-1)));
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

    LongRangeSet first = fromTypeStrict(PsiType.CHAR).without(45);
    LongRangeSet second =
      fromTypeStrict(PsiType.CHAR).without(32).without(40).without(44).without(45).without(46).without(58).without(59).without(61);
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
    LongRangeSet longNotChar = fromTypeStrict(PsiType.LONG).subtract(fromTypeStrict(PsiType.CHAR));
    map.put(longNotChar, "longNotChar");

    assertEquals("empty", map.get(empty()));
    assertEquals("10-10", map.get(point(10)));
    assertEquals("10-11", map.get(range(10, 11)));
    assertEquals("10-12", map.get(range(10, 12)));
    assertNull(map.get(range(11, 11)));
    assertEquals("longNotChar", map.get(fromTypeStrict(PsiType.LONG).subtract(fromTypeStrict(PsiType.CHAR))));
  }

  @Test
  public void testIntersects() {
    assertFalse(empty().intersects(fromTypeStrict(PsiType.LONG)));
    assertTrue(point(Long.MIN_VALUE).intersects(fromTypeStrict(PsiType.LONG)));
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
  public void testUnite() {
    assertEquals("{1}", point(1).unite(empty()).toString());
    assertEquals("{1}", empty().unite(point(1)).toString());
    assertEquals("{1}", point(1).unite(point(1)).toString());
    assertEquals("{1, 2}", point(2).unite(point(1)).toString());
    assertEquals("{1, 2}", point(1).unite(point(2)).toString());
    assertEquals("{1, 3}", point(1).unite(point(3)).toString());
    assertEquals("{1, 3}", point(3).unite(point(1)).toString());
    LongRangeSet twoTen = range(2, 10);
    assertEquals("{0, 2..10}", point(0).unite(twoTen).toString());
    assertEquals("{1..10}", point(1).unite(twoTen).toString());
    assertEquals("{2..10}", point(2).unite(twoTen).toString());
    assertEquals("{2..11}", point(11).unite(twoTen).toString());
    assertEquals("{2..10, 12}", point(12).unite(twoTen).toString());
    assertEquals("{2..10, 12}", point(12).unite(twoTen).toString());
    LongRangeSet set = range(10, 20).unite(range(30, 40)).unite(range(42, 50));
    assertEquals("{10..20, 30..40, 42..50}", set.toString());
    assertEquals("{8, 10..20, 30..40, 42..50}", set.unite(point(8)).toString());
    assertEquals("{9..20, 30..40, 42..50}", set.unite(point(9)).toString());
    assertEquals("{10..21, 30..40, 42..50}", set.unite(point(21)).toString());
    assertEquals("{10..20, 22, 30..40, 42..50}", set.unite(point(22)).toString());
    assertEquals("{10..20, 29..40, 42..50}", set.unite(point(29)).toString());
    assertEquals("{10..20, 30..50}", set.unite(point(41)).toString());
    assertEquals("{10..20, 30..40, 42..51}", set.unite(point(51)).toString());
    assertEquals("{10..20, 30..40, 42..50, 52}", set.unite(point(52)).toString());
    assertEquals("{10..40}", range(20, 30).unite(range(10, 40)).toString());
    assertEquals("{10..40}", range(10, 40).unite(range(20, 30)).toString());
    assertEquals("{10..30}", range(10, 20).unite(range(20, 30)).toString());
    assertEquals("{10..30}", range(20, 30).unite(range(10, 20)).toString());
    assertEquals("{10..30}", range(10, 19).unite(range(20, 30)).toString());
    assertEquals("{10..30}", range(20, 30).unite(range(10, 19)).toString());
    assertEquals("{10..18, 20..30}", range(20, 30).unite(range(10, 18)).toString());
    assertEquals("{10..18, 20..30}", range(10, 18).unite(range(20, 30)).toString());

    assertEquals("{-4..8, 10..20, 30..40, 42..50}", range(-4, 8).unite(set).toString());
    assertEquals("{-4..20, 30..40, 42..50}", range(-4, 9).unite(set).toString());
    assertEquals("{-4..50}", range(-4, 41).unite(set).toString());
    assertEquals("{-4..51}", range(-4, 51).unite(set).toString());
    assertEquals("{10..20, 30..40, 42..60}", range(51, 60).unite(set).toString());
    assertEquals("{10..20, 30..40, 42..50, 52..60}", range(52, 60).unite(set).toString());
    assertEquals("{10..20, 30..40, 42..50}", range(12, 14).unite(set).toString());
    assertEquals("{10..40, 42..50}", range(12, 34).unite(set).toString());
    assertEquals("{10..50}", range(10, 41).unite(set).toString());
    assertEquals("{10..50}", range(12, 41).unite(set).toString());
    assertEquals("{10..50}", range(20, 41).unite(set).toString());
    assertEquals("{10..50}", range(21, 41).unite(set).toString());
    assertEquals("{10..20, 22..50}", range(22, 41).unite(set).toString());
    assertEquals("{10..50}", range(12, 42).unite(set).toString());
    assertEquals("{10..50}", range(10, 50).unite(set).toString());
  }

  @Test
  public void testUniteRandomized() {
    Random r = new Random(1);

    for (int i = 0; i < 100; i++) {
      List<LongRangeSet> intervals = new ArrayList<>();
      for (int j = 0; j < 20; j++) {
        int from = r.nextInt(100);
        intervals.add(range(from, from + r.nextInt(25)));
      }
      String start = "i=" + i + ":" + intervals;
      LongRangeSet union = empty();
      for (LongRangeSet interval : intervals) {
        union = union.unite(interval);
      }
      while (intervals.size() > 1) {
        LongRangeSet set1 = intervals.remove(r.nextInt(intervals.size()));
        LongRangeSet set2 = intervals.remove(r.nextInt(intervals.size()));
        LongRangeSet result = set1.unite(set2);
        intervals.add(result);
      }
      assertEquals(start, union, intervals.get(0));
    }
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
      assertEquals(message, intersection, right.subtract(fromTypeStrict(PsiType.LONG).subtract(left)));
      assertEquals(message, intersection, left.subtract(fromTypeStrict(PsiType.LONG).subtract(right)));
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
    assertEquals("{0}", fromConstantStrict(0).toString());
    assertEquals("{0}", fromConstantStrict(0L).toString());
    assertEquals("{1}", fromConstantStrict((byte)1).toString());
    assertEquals("{97}", fromConstantStrict('a').toString());
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
    assertEquals(fromTypeStrict(PsiType.LONG), range(100, 200).fromRelation(RelationType.NE));
    assertEquals("{Long.MIN_VALUE..99, 101..Long.MAX_VALUE}", point(100).fromRelation(RelationType.NE).toString());
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
    assertEquals(range(0, Long.MAX_VALUE).unite(point(Long.MIN_VALUE)), all().abs(true));
    assertEquals(range(100, Integer.MAX_VALUE).unite(point(Integer.MIN_VALUE)), range(Integer.MIN_VALUE, -100).abs(false));
    assertEquals(range(100, Integer.MAX_VALUE + 1L), range(Integer.MIN_VALUE, -100).abs(true));
    LongRangeSet set = range(-900, 1000).subtract(range(-800, -600)).subtract(range(-300, 100)).subtract(range(500, 700));
    assertEquals("{-900..-801, -599..-301, 101..499, 701..1000}", set.toString());
    assertEquals("{101..599, 701..1000}", set.abs(false).toString());
  }

  @Test
  public void testNegate() {
    assertTrue(empty().negate(true).isEmpty());
    assertEquals(point(Long.MAX_VALUE), point(Long.MIN_VALUE + 1).negate(true));
    assertEquals(point(Long.MIN_VALUE), point(Long.MIN_VALUE).negate(true));
    assertEquals(point(Integer.MIN_VALUE), point(Integer.MIN_VALUE).negate(false));
    assertEquals(point(Integer.MAX_VALUE + 1L), point(Integer.MIN_VALUE).negate(true));
    assertEquals(range(-200, -100), range(100, 200).negate(true));
    assertEquals(range(-200, 1), range(-1, 200).negate(true));
    assertEquals(range(-200, 200), range(-200, 200).negate(false));
    assertEquals(range(-200, 201), range(-201, 200).negate(false));
    assertEquals(all(), all().negate(true));
    assertEquals(range(100, Integer.MAX_VALUE).unite(point(Integer.MIN_VALUE)), range(Integer.MIN_VALUE, -100).negate(false));
    assertEquals(point(Long.MAX_VALUE).unite(point(Long.MIN_VALUE)), range(Long.MIN_VALUE, Long.MIN_VALUE + 1).negate(true));
    assertEquals(range(100, Integer.MAX_VALUE + 1L), range(Integer.MIN_VALUE, -100).negate(true));
    LongRangeSet set = range(-900, 1000).subtract(range(-800, -600)).subtract(range(-300, 100)).subtract(range(500, 700));
    assertEquals("{-900..-801, -599..-301, 101..499, 701..1000}", set.toString());
    assertEquals("{-1000..-701, -499..-101, 301..599, 801..900}", set.negate(false).toString());
    checkNegate(modRange(-15, 100, 10, 0b1011), "{-100..10}: <0, 7, 9> mod 10", true);
    checkNegate(modRange(-15, 100, 2, 0b10), "{-99..15}: odd", false);
  }

  @Test
  public void testCastTo() {
    PsiPrimitiveType[] types = {PsiType.BYTE, PsiType.SHORT, PsiType.CHAR, PsiType.INT, PsiType.LONG};
    for (PsiPrimitiveType type : types) {
      assertTrue(empty().castTo(type).isEmpty());
      assertEquals(point(0), point(0).castTo(type));
    }
    assertEquals(point(0x1234_5678_9ABC_DEF0L), point(0x1234_5678_9ABC_DEF0L).castTo(PsiType.LONG));
    assertEquals(point(0x9ABC_DEF0), point(0x1234_5678_9ABC_DEF0L).castTo(PsiType.INT));
    assertEquals(point(0xDEF0), point(0x1234_5678_9ABC_DEF0L).castTo(PsiType.CHAR));
    assertEquals(point(-8464), point(0x1234_5678_9ABC_DEF0L).castTo(PsiType.SHORT));
    assertEquals(point(-16), point(0x1234_5678_9ABC_DEF0L).castTo(PsiType.BYTE));
    LongRangeSet longSet = fromTypeStrict(PsiType.LONG);
    assertNotNull(longSet);
    LongRangeSet byteSet = fromTypeStrict(PsiType.BYTE);
    assertNotNull(byteSet);
    for (PsiPrimitiveType type : types) {
      LongRangeSet set = fromTypeStrict(type);
      assertNotNull(set);
      assertEquals(set, set.castTo(type));
      assertEquals(set, longSet.castTo(type));
      assertEquals(type.equals(PsiType.CHAR) ? range(0, 127).unite(range(0xFF80, 0xFFFF)) : byteSet, byteSet.castTo(type));
    }
    checkCast(range(-10, 1000), "{-128..127}", PsiType.BYTE);
    checkCast(range(-10, 200), "{-128..-56, -10..127}", PsiType.BYTE);
    checkCast(range(-1, 255), "{0..255, 65535}", PsiType.CHAR);
    checkCast(range(0, 100000), "{-32768..32767}", PsiType.SHORT);
    checkCast(range(0, 50000), "{-32768..-15536, 0..32767}", PsiType.SHORT);
    assertEquals(fromTypeStrict(PsiType.INT), range(Long.MIN_VALUE, Integer.MAX_VALUE-1).castTo(PsiType.INT));
  }

  @Test
  public void testBitwiseAnd() {
    assertTrue(empty().bitwiseAnd(all()).isEmpty());
    assertTrue(all().bitwiseAnd(empty()).isEmpty());
    assertEquals(all(), all().bitwiseAnd(all()));
    assertEquals("{0, 16}", all().bitwiseAnd(point(16)).toString());
    assertEquals("{0, 1}", all().bitwiseAnd(point(1)).toString());
    assertEquals("{0..24}: <0> mod 8", all().bitwiseAnd(point(24)).toString());
    assertEquals("{0..25}: <0, 1> mod 8", all().bitwiseAnd(point(25)).toString());
    assertEquals("{Long.MIN_VALUE..Long.MAX_VALUE-1}: even", all().bitwiseAnd(point(-2)).toString());
    assertEquals("{Long.MIN_VALUE..9223372036854775805}: <0, 1> mod 4", all().bitwiseAnd(point(-3)).toString());
    assertEquals("{Long.MIN_VALUE..9223372036854775804}: <0> mod 4", all().bitwiseAnd(point(-4)).toString());
    assertEquals(range(0, 15), all().bitwiseAnd(range(10, 15)));
    assertEquals(range(0, 31), all().bitwiseAnd(range(16, 24)));

    checkBitwiseAnd(range(0, 3), range(4, 7), "{0..3}");
    checkBitwiseAnd(range(3, 4), range(3, 4), "{0..7}"); // 0,3,4,7 actually
    checkBitwiseAnd(range(-20, 20), point(8), "{0, 8}");
    checkBitwiseAnd(point(3).unite(point(5)), point(3).unite(point(5)), "{1, 3, 5}");
    checkBitwiseAnd(range(-10, 10), range(-20, 5), "{-32..15}");
    checkBitwiseAnd(range(-30, -20).unite(range(20, 33)), point(-10).unite(point(10)), "{-32..-26, 0..54}");
  }

  @Test
  public void testMod() {
    assertEquals(empty(), empty().mod(all()));
    assertEquals(empty(), all().mod(empty()));
    assertEquals(empty(), point(1).mod(empty()));
    assertEquals(empty(), point(1).unite(point(3)).mod(empty()));

    assertEquals(point(10), point(110).mod(point(100)));
    checkMod(range(10, 20), range(30, 40), "{10..20}");
    checkMod(range(-10, 10), range(20, 30), "{-10..10}");
    checkMod(point(0), range(-100, -50).unite(range(20, 80)), "{0}");
    checkMod(point(30), range(10, 40), "{0..30}");
    checkMod(point(-30), range(-10, 40), "{-30..0}");
    checkMod(point(Long.MIN_VALUE), range(-10, 40), "{-39..0}");
    checkMod(range(-10, 40), point(Long.MIN_VALUE), "{-10..40}");
    checkMod(range(-30, -20), point(23), "{-22..0}");
    checkMod(point(10), range(30, 40), "{10}");
    checkMod(range(-10, 40), point(Long.MIN_VALUE).unite(point(70)), "{-10..40}");
    checkMod(range(-10, 40), point(Long.MIN_VALUE).unite(point(0)), "{-10..40}");
    checkMod(point(10), point(Long.MIN_VALUE).unite(point(0)), "{0, 10}");
    checkMod(range(0, 10).unite(range(30, 50)), range(-20, -10).unite(range(15, 25)), "{0..24}");
    checkMod(point(10), point(0), "{}");
    checkMod(range(0, 10), point(0), "{}");
    checkMod(range(Long.MIN_VALUE, Long.MIN_VALUE + 3), point(Long.MIN_VALUE), "{-9223372036854775807..-9223372036854775805, 0}");
    checkMod(range(Long.MAX_VALUE - 3, Long.MAX_VALUE), point(Long.MAX_VALUE), "{0..Long.MAX_VALUE-1}");
    checkMod(range(0, 10).mul(point(4), false), point(10), "{0..8}: <0, 2, 4, 6, 8> mod 10");
    checkMod(range(-1, 10).mul(point(4), false), point(10), "{-4..8}: <0, 2, 4, 6, 8> mod 10");
    checkMod(range(-2, 10).mul(point(3), false).minus(point(1), false), point(6), "{-4..5}: <2> mod 3");
  }

  @Test
  public void testDiv() {
    assertEquals(empty(), empty().div(all(), true));
    assertEquals(empty(), all().div(empty(), true));
    assertEquals(empty(), point(1).div(empty(), true));
    assertEquals(empty(), point(1).div(point(3), true).div(empty(), true));
    assertEquals(all(), all().div(all(), true));
    assertEquals(empty(), all().div(point(0), true));
    assertEquals(all(), all().div(point(1), true));
    assertEquals(all(), all().div(point(-1), true));
    assertEquals(point(11), point(110).div(point(10), true));

    checkDiv(range(1, 20), range(1, 5), true, "{0..20}");
    checkDiv(range(1, 20), range(-5, -1), true, "{-20..0}");
    checkDiv(range(-20, -1), range(1, 5), true, "{-20..0}");
    checkDiv(range(-20, -1), range(-5, -1), true, "{0..20}");
    checkDiv(range(-10, 10), range(2, 4), true, "{-5..5}");
    checkDiv(range(100, 120), range(-2, 2), true, "{-120..-50, 50..120}");
    checkDiv(range(Integer.MIN_VALUE, Integer.MIN_VALUE + 20), range(-2, 2), true,
             "{Integer.MIN_VALUE..-1073741814, 1073741814..2147483648}");
    checkDiv(range(Integer.MIN_VALUE, Integer.MIN_VALUE + 20), range(-2, 2), false,
             "{Integer.MIN_VALUE..-1073741814, 1073741814..Integer.MAX_VALUE}");
    checkDiv(range(Integer.MIN_VALUE, Integer.MIN_VALUE + 20), range(-2, -1), true,
             "{1073741814..2147483648}");
    checkDiv(range(Integer.MIN_VALUE, Integer.MIN_VALUE + 20), range(-2, -1), false,
             "{Integer.MIN_VALUE, 1073741814..Integer.MAX_VALUE}");
  }

  @Test
  public void testShr() {
    assertEquals(empty(), empty().shiftRight(all(), true));
    assertEquals(empty(), all().shiftRight(empty(), true));
    assertEquals(all(), all().shiftRight(all(), true));
    assertEquals(fromTypeStrict(PsiType.INT), all().shiftRight(point(32), true));
    assertEquals(fromTypeStrict(PsiType.SHORT), fromTypeStrict(PsiType.INT).shiftRight(point(16), false));
    assertEquals(fromTypeStrict(PsiType.BYTE), fromTypeStrict(PsiType.INT).shiftRight(point(24), false));
    assertEquals(range(-1, 0), fromTypeStrict(PsiType.INT).shiftRight(point(31), false));

    checkShr(range(-20, 20), point(31), false, "{-1, 0}");
    checkShr(range(-20, 20), point(31), true, "{-1, 0}");
    checkShr(range(-20, 20), range(1, 3), true, "{-10..10}");
    checkShr(range(-20, 20), range(3, 5), true, "{-3..2}");
    checkShr(range(1000000, 1000020), range(3, 5), true, "{31250..125002}");
  }

  @Test
  public void testShl() {
    assertEquals(empty(), empty().shiftLeft(all(), true));
    assertEquals(empty(), all().shiftLeft(empty(), true));
    assertEquals(all(), all().shiftLeft(all(), true));
    checkShl(point(1), point(3), false, "{8}");
    checkShl(range(0, 10), point(3), false, "{0..80}: <0> mod 8");
    checkShl(range(0, 15), point(28), false, "{Integer.MIN_VALUE..2147483584}: <0> mod 64");
    checkShl(range(0, 15), point(28), true, "{0..4026531840}: <0> mod 64");
  }

  @Test
  public void testUShr() {
    assertEquals(empty(), empty().unsignedShiftRight(all(), true));
    assertEquals(empty(), all().unsignedShiftRight(empty(), true));
    assertEquals(all(), all().unsignedShiftRight(all(), true));
    assertEquals(range(0, 4294967295L), all().unsignedShiftRight(point(32), true));
    assertEquals(fromTypeStrict(PsiType.CHAR), fromTypeStrict(PsiType.INT).unsignedShiftRight(point(16), false));
    assertEquals(range(0, 255), fromTypeStrict(PsiType.INT).unsignedShiftRight(point(24), false));
    assertEquals(range(0, 1), fromTypeStrict(PsiType.INT).unsignedShiftRight(point(31), false));

    checkUShr(range(-20, 20), point(31), false, "{0, 1}");
    checkUShr(range(-20, 20), point(31), true, "{0, 8589934591}");
    checkUShr(range(-20, 20), range(1, 3), true, "{0..10, 2305843009213693949..Long.MAX_VALUE}");
    checkUShr(range(-20, 20), range(1, 3), false, "{0..10, 536870909..Integer.MAX_VALUE}");
    checkUShr(range(-20, 20), range(3, 5), true, "{0..2, 576460752303423487..2305843009213693951}");
    checkUShr(range(-20, 20), range(3, 5), false, "{0..2, 134217727..536870911}");
    checkUShr(range(1000000, 1000020), range(3, 5), true, "{31250..125002}");
  }

  @Test
  public void testContains() {
    assertTrue(range(0, 10).contains(5));
    assertTrue(range(0, 10).unite(range(13, 20)).contains(point(5)));
    assertTrue(range(0, 10).unite(range(13, 20)).contains(empty()));
    assertFalse(range(0, 10).unite(range(13, 20)).contains(point(12)));
    assertFalse(range(0, 10).unite(range(13, 20)).contains(range(9, 15)));
    assertTrue(range(0, 10).unite(range(13, 20)).contains(range(2, 8).unite(range(15, 17))));
  }

  @Test
  public void testAdd() {
    checkAdd(empty(), empty(), true, "{}");
    checkAdd(empty(), point(0), true, "{}");
    checkAdd(empty(), range(0, 10), true, "{}");
    checkAdd(empty(), range(0, 10).unite(range(15, 20)), true, "{}");

    checkAdd(point(5), point(10), false, "{15}");
    checkAdd(point(Integer.MAX_VALUE), point(Integer.MAX_VALUE), false, "{-2}");
    checkAdd(point(Integer.MAX_VALUE), point(Integer.MAX_VALUE), true, "{" + 0xFFFF_FFFEL + "}");
    checkAdd(range(0, 10), point(10), false, "{10..20}");
    checkAdd(range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), point(1), true, "{2147483638..2147483648}");
    checkAdd(range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), point(1), false, "{Integer.MIN_VALUE, 2147483638..Integer.MAX_VALUE}");
    checkAdd(range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), point(10), false, "{Integer.MIN_VALUE..-2147483639, Integer.MAX_VALUE}");
    checkAdd(range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), point(11), false, "{Integer.MIN_VALUE..-2147483638}");

    checkAdd(range(0, 10), range(20, 30), true, "{20..40}");
    checkAdd(range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), range(0, 10), true, "{2147483637..2147483657}");
    checkAdd(range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), range(0, 10), false, "{Integer.MIN_VALUE..-2147483639, 2147483637..Integer.MAX_VALUE}");

    checkAdd(range(10, 20).unite(range(40, 50)), range(0, 3).unite(range(5, 7)), true, "{10..27, 40..57}");
    
    checkAdd(range(-1, 10).mul(point(2), false), point(3), false, "{1..23}: odd");
    checkAdd(range(-1, 10).mul(point(2), false), point(-10), false, "{-12..10}: even");
    checkAdd(range(-1, 10).mul(point(3), false), point(-1), false, "{-4..29}: <2> mod 3");
    checkAdd(point(10), range(-1, 10).mul(point(2), false), false, "{8..30}: even");
    checkAdd(range(-1, 10).mul(point(2), false), range(-1, 10).mul(point(2), false), false, "{-4..40}: even");
    LongRangeSet m1to10by3plus1 = range(-1, 10).mul(point(3), false).plus(point(1), false);
    checkAdd(m1to10by3plus1, m1to10by3plus1, false, "{-4..62}: <2> mod 3");

    LongRangeSet intDomain = range(Integer.MIN_VALUE, Integer.MAX_VALUE);
    assertEquals(intDomain, intDomain.plus(point(20), false));
    assertEquals(intDomain.without(20), intDomain.without(0).plus(point(20), false));
    assertEquals(all().without(20), all().without(0).plus(point(20), true));
    assertEquals(intDomain, range(20, 30).unite(range(40, 50)).plus(intDomain, false));
    assertEquals(intDomain, range(Integer.MIN_VALUE, 2).plus(range(-2, Integer.MAX_VALUE), false));
    assertEquals(all(), range(Long.MIN_VALUE, 2).plus(range(-2, Long.MAX_VALUE), true));
    assertEquals(all(), range(-100, Long.MAX_VALUE).plus(all(), true));
  }
  
  @Test
  public void testMul() {
    checkMul(empty(), empty(), true, "{}");
    checkMul(empty(), point(0), true, "{}");
    checkMul(empty(), range(0, 10), true, "{}");
    checkMul(empty(), range(0, 10).unite(range(15, 20)), true, "{}");

    checkMul(point(5), point(10), false, "{50}");
    checkMul(point(2_000_000_000), point(2), false, "{-294967296}");
    checkMul(point(2_000_000_000), point(2), true, "{4000000000}");
    checkMul(point(Integer.MIN_VALUE), point(Integer.MIN_VALUE), false, "{0}");
    checkMul(point(Integer.MIN_VALUE), point(Integer.MIN_VALUE), true, "{4611686018427387904}");
    checkMul(point(1), point(10), false, "{10}");
    checkMul(point(0), point(10), false, "{0}");
    checkMul(point(-1), point(10), false, "{-10}");

    checkMul(point(1), range(10, 20).unite(range(30, 40)), false, "{10..20, 30..40}");
    checkMul(point(0), range(10, 20).unite(range(30, 40)), false, "{0}");
    checkMul(point(-1), range(10, 20).unite(range(30, 40)), false, "{-40..-30, -20..-10}");
    checkMul(point(-1), range(Integer.MIN_VALUE, Integer.MIN_VALUE+30), false, "{Integer.MIN_VALUE, 2147483618..Integer.MAX_VALUE}");
    checkMul(point(-1), range(Integer.MIN_VALUE, Integer.MIN_VALUE+30), true, "{2147483618..2147483648}");

    checkMul(point(2), range(10, 20), false, "{20..40}: even");
    checkMul(point(-2), range(10, 20), false, "{-40..-20}: even");
    checkMul(point(2), range(-20, -10), false, "{-40..-20}: even");
    checkMul(point(2), range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), false, "{Integer.MIN_VALUE..Integer.MAX_VALUE-1}: even");
    checkMul(point(2), range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), true, "{4294967274..4294967294}: even");
    checkMul(point(3), range(-5, 15), false, "{-15..45}: <0> mod 3");
    checkMul(point(3), range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), false, "{Integer.MIN_VALUE..Integer.MAX_VALUE}");
    checkMul(point(6), range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), false, "{Integer.MIN_VALUE..Integer.MAX_VALUE-1}: even");
    checkMul(point(3), range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), true, "{6442450911..6442450941}: <0> mod 3");
    checkMul(point(6), range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE), true, "{12884901822..12884901882}: <0> mod 6");
  }

  @Test
  public void testModRange() {
    assertEquals("{}", modRange(0, 10, 100, 0).toString());
    assertEquals("{0..10}", modRange(0, 10, 100, 1).toString());
    assertEquals("{0}", modRange(0, 10, 50, 1).toString());
    assertEquals("{0}", modRange(-5, 5, 50, 1).toString());
    assertEquals("{}", modRange(3, 7, 50, 1).toString());
    assertEquals("{-50..50}: <0> mod 50", modRange(-52, 52, 50, 1).toString());
    assertEquals("{Long.MIN_VALUE..Long.MAX_VALUE-1}: even", modRange(Long.MIN_VALUE, Long.MAX_VALUE, 8, 0b01010101).toString());
    assertEquals("{-9223372036854775806..Long.MAX_VALUE-1}: <2> mod 4", modRange(Long.MIN_VALUE, Long.MAX_VALUE, 8, 0b01000100).toString());
    assertEquals("{2..15}: <2, 5> mod 10", modRange(0, 20, 10, 0b100100).toString());
    assertEquals("{0..100}", modRange(0, 100, 10, 0b1111111111).toString());
  }

  @Test
  public void testModRangeContains() {
    LongRangeSet set = modRange(0, 24, 10, 0b111100);
    assertEquals("{2..24}: <2, 3, 4, 5> mod 10", set.toString());
    Set<Integer> members = ContainerUtil.set(2, 3, 4, 5, 12, 13, 14, 15, 22, 23, 24);
    assertFalse(set.contains(-1));
    assertFalse(set.contains(26));
    assertEquals(members, set.stream().mapToObj(val -> (int)val).collect(Collectors.toSet()));
    assertTrue(set.contains(range(2, 5)));
    assertFalse(set.contains(range(2, 6)));
    assertTrue(set.contains(range(12, 15)));
    assertFalse(set.contains(range(11, 15)));
    assertTrue(set.contains(modRange(0, 25, 10, 0b11000)));
    assertFalse(set.contains(modRange(0, 23, 10, 0b11001)));

    LongRangeSet wrappedSet = modRange(0, 24, 10, 0b1110000011);
    assertTrue(wrappedSet.contains(range(8, 10)));
    assertTrue(wrappedSet.contains(range(7, 11)));
    assertFalse(wrappedSet.contains(range(6, 11)));
    assertFalse(wrappedSet.contains(range(7, 12)));
    LongRangeSet byFour = modRange(0, 100, 4, 0b0111);
    assertFalse(byFour.contains(range(0, 70)));
    assertTrue(byFour.contains(modRange(0, 70, 4, 0b0001)));
    assertTrue(byFour.contains(modRange(0, 70, 2, 0b01)));
    assertFalse(byFour.contains(modRange(0, 70, 2, 0b10)));
    assertFalse(byFour.contains(modRange(0, 70, 6, 0b011101)));
    assertTrue(byFour.contains(modRange(0, 70, 6, 0b010001)));
  }

  @Test
  public void testModRangeIntersects() {
    LongRangeSet set = modRange(0, 24, 10, 0b111100);
    assertTrue(set.intersects(range(0, 2)));
    assertFalse(set.intersects(range(0, 1)));
    assertTrue(set.intersects(range(24, 27)));
    assertFalse(set.intersects(range(25, 27)));
    assertTrue(set.intersects(range(10, 12)));
    assertFalse(set.intersects(range(6, 9)));
    assertFalse(set.intersects(range(6, 9).unite(range(16, 19))));
    assertTrue(set.intersects(range(6, 9).unite(range(15, 19))));
    assertTrue(set.intersects(modRange(10, 13, 10, 0b1000)));
    assertFalse(set.intersects(modRange(0, 24, 10, 0b1111000011)));
    LongRangeSet byFour = modRange(0, 100, 4, 0b0111);
    assertTrue(byFour.intersects(modRange(0, 70, 6, 0b011101)));
    assertFalse(byFour.intersects(modRange(0, 70, 8, 0b10001000)));
  }

  @Test
  public void testModRangeIntersect() {
    LongRangeSet set = modRange(0, 24, 10, 0b111100);
    assertEquals("{2}", set.intersect(range(0, 2)).toString());
    assertEquals("{}", set.intersect(range(0, 1)).toString());
    assertEquals("{24}", set.intersect(range(24, 27)).toString());
    assertEquals("{}", set.intersect(range(25, 27)).toString());
    assertEquals("{12}", set.intersect(range(10, 12)).toString());
    assertEquals("{12, 13}", set.intersect(range(10, 13)).toString());
    assertEquals("{12..15}", set.intersect(range(6, 15)).toString());
    assertEquals("{}", set.intersect(range(6, 9)).toString());
    assertEquals("{5..15}: <2, 3, 4, 5> mod 10", set.intersect(range(5, 15)).toString());
    assertEquals("{13}", set.intersect(modRange(10, 13, 10, 0b1000)).toString());
    assertEquals("{12, 13}", set.intersect(modRange(10, 13, 10, 0b1100)).toString());
    assertEquals("{2..23}: <2, 3> mod 10", set.intersect(modRange(-20, 100, 10, 0b1111)).toString());
    assertEquals("{}", set.intersect(modRange(0, 24, 10, 0b1111000011)).toString());
    assertEquals("{5..15}: <5> mod 10", set.intersect(modRange(0, 24, 5, 0b1)).toString());
    assertEquals("{3..24}: <3, 4, 12, 13, 15, 22, 24, 25> mod 30", set.intersect(modRange(0, 24, 3, 0b11)).toString());
    assertEquals("{3..15}: <0, 3, 4, 12, 13> mod 15", set.intersect(modRange(0, 16, 3, 0b11)).toString());
    
    LongRangeSet even = modRange(0, 15, 2, 0b1);
    even = even.intersect(point(10).fromRelation(RelationType.NE));
    assertEquals("{0..14}: <0, 2, 4, 6, 8, 12, 14> mod 16", even.toString());
    even = even.intersect(point(9).fromRelation(RelationType.NE));
    assertEquals("{0..14}: <0, 2, 4, 6, 8, 12, 14> mod 16", even.toString());
    even = even.intersect(point(2).fromRelation(RelationType.NE));
    assertEquals("{0..14}: <0, 4, 6> mod 8", even.toString());
    even = even.intersect(point(4).fromRelation(RelationType.NE));
    assertEquals("{0..14}: <0, 6, 8, 12, 14> mod 16", even.toString());
    even = even.intersect(point(6).fromRelation(RelationType.NE));
    assertEquals("{0..14}: <0, 8, 12, 14> mod 16", even.toString());
  }
  
  @Test
  public void testModRangeUnite() {
    assertEquals("{0..20}: <0> mod 5", modRange(0, 10, 5, 0b1).unite(modRange(15, 20, 5, 0b1)).toString());
    assertEquals("{0..10, 20}", modRange(0, 10, 5, 0b1).unite(modRange(16, 20, 5, 0b1)).toString());
    assertEquals("{0..10, 16}", modRange(0, 11, 5, 0b1).unite(modRange(15, 20, 5, 0b10)).toString());
    assertEquals("{0..16}: <0, 1> mod 5", modRange(0, 11, 5, 0b1).unite(modRange(11, 20, 5, 0b10)).toString());
    assertEquals("{0..19}: <0, 1, 3, 5, 7, 9> mod 10", modRange(0, 11, 5, 0b1).unite(modRange(11, 20, 2, 0b10)).toString());
    assertEquals("{0..100}", modRange(0, 100, 2, 0b1).unite(point(1)).toString());
  }
  
  @Test
  public void testFromRemainder() {
    assertEquals("{-9223372036854775805..9223372036854775805}: <0> mod 5", fromRemainder(5, point(0)).toString());
    assertEquals("{1..Long.MAX_VALUE-1}: <1> mod 5", fromRemainder(5, point(1)).toString());
    assertEquals("{1..Long.MAX_VALUE}: <1, 2, 3, 4> mod 5", fromRemainder(5, range(1, 4)).toString());
    assertEquals("{Long.MIN_VALUE..-3}: <2> mod 5", fromRemainder(5, point(-3)).toString());
    assertEquals("{Long.MIN_VALUE..Long.MAX_VALUE}: <1, 2, 3, 4> mod 5", fromRemainder(5, range(1, 4).unite(range(-4, -1))).toString());
  }

  void checkAdd(LongRangeSet addend1, LongRangeSet addend2, boolean isLong, String expected) {
    LongRangeSet result = addend1.plus(addend2, isLong);
    assertEquals(result, addend2.plus(addend1, isLong)); // commutative
    checkBinOp(addend1, addend2, result, x -> true, isLong ? Long::sum : (a, b) -> (int)(a + b), expected, "+");
  }

  void checkMul(LongRangeSet multiplier1, LongRangeSet multiplier2, boolean isLong, String expected) {
    LongRangeSet result = multiplier1.mul(multiplier2, isLong);
    assertEquals(result, multiplier2.mul(multiplier1, isLong)); // commutative
    checkBinOp(multiplier1, multiplier2, result, x -> true, isLong ? (a, b) -> a * b : (a, b) -> (int)(a * b), expected, "*");
  }

  void checkMod(LongRangeSet dividendRange, LongRangeSet divisorRange, String expected) {
    LongRangeSet result = dividendRange.mod(divisorRange);
    checkBinOp(dividendRange, divisorRange, result, divisor -> divisor != 0, (a, b) -> a % b, expected, "%");
  }

  void checkDiv(LongRangeSet dividendRange, LongRangeSet divisorRange, boolean isLong, String expected) {
    LongRangeSet result = dividendRange.div(divisorRange, isLong);
    checkBinOp(dividendRange, divisorRange, result, divisor -> divisor != 0, (a, b) -> isLong ? a / b : ((int)a / (int)b), expected, "/");
  }

  void checkShr(LongRangeSet arg, LongRangeSet shiftSize, boolean isLong, String expected) {
    LongRangeSet result = arg.shiftRight(shiftSize, isLong);
    checkBinOp(arg, shiftSize, result, x -> true, (a, b) -> isLong ? a >> b : ((int)a >> (int)b), expected, ">>");
  }

  void checkShl(LongRangeSet arg, LongRangeSet shiftSize, boolean isLong, String expected) {
    LongRangeSet result = arg.shiftLeft(shiftSize, isLong);
    //noinspection IntegerMultiplicationImplicitCastToLong -- possible overflow is intended
    checkBinOp(arg, shiftSize, result, x -> true, (a, b) -> isLong ? a << b : ((int)a << (int)b), expected, "<<");
  }

  void checkUShr(LongRangeSet arg, LongRangeSet shiftSize, boolean isLong, String expected) {
    LongRangeSet result = arg.unsignedShiftRight(shiftSize, isLong);
    checkBinOp(arg, shiftSize, result, x -> true, (a, b) -> isLong ? a >>> b : ((int)a >>> (int)b), expected, ">>>");
  }

  void checkBitwiseAnd(LongRangeSet range1, LongRangeSet range2, String expected) {
    LongRangeSet result = range1.bitwiseAnd(range2);
    assertEquals(result, range2.bitwiseAnd(range1)); // commutative
    checkBinOp(range1, range2, result, x -> true, (a, b) -> a & b, expected, "&");
  }

  void checkBinOp(LongRangeSet op1,
                  LongRangeSet op2,
                  LongRangeSet result,
                  LongPredicate filter,
                  LongBinaryOperator operator,
                  String expected,
                  String sign) {
    assertEquals(expected, result.toString());
    String errors = op1.stream()
      .mapToObj(a -> op2.stream()
        .filter(filter)
        .filter(b -> !result.contains(operator.applyAsLong(a, b)))
        .mapToObj(b -> a + " " + sign + " " + b + " = " + operator.applyAsLong(a, b)))
      .flatMap(Function.identity())
      .collect(Collectors.joining("\n"));
    if (!errors.isEmpty()) {
      fail("Expected range " + expected + " is not satisfied:\n" + errors);
    }
  }

  void checkCast(LongRangeSet operand, String expected, PsiPrimitiveType castType) {
    LongRangeSet result = operand.castTo(castType);
    assertEquals(expected, result.toString());
    checkUnOp(operand, result,
              castType.equals(PsiType.CHAR) ? x -> (char)x : x -> ((Number)TypeConversionUtil.computeCastTo(x, castType)).longValue(),
              expected, castType.getCanonicalText());
  }

  void checkNegate(LongRangeSet operand, String expected, boolean isLong) {
    LongRangeSet result = operand.negate(isLong);
    assertEquals(expected, result.toString());
    checkUnOp(operand, result, isLong ? x -> -x : x -> (int)-x, expected, "-");
  }

  void checkUnOp(LongRangeSet operand,
                 LongRangeSet result,
                 LongUnaryOperator operator,
                 String expected,
                 String sign) {
    assertEquals(expected, result.toString());
    String errors = operand.stream()
      .filter(arg -> !result.contains(operator.applyAsLong(arg)))
      .mapToObj(arg -> sign + " (" + arg + ") = " + operator.applyAsLong(arg))
      .collect(Collectors.joining("\n"));
    if (!errors.isEmpty()) {
      fail("Expected range " + expected + " is not satisfied:\n" + errors);
    }
  }


}
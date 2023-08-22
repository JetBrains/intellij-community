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
package com.intellij.java.codeInsight.intention;

import com.intellij.psi.PsiTypes;
import com.intellij.util.text.LiteralFormatUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnderscoresInLiteralsFormatterTest {
  @Test
  public void testIntegers() {
    assertEquals("0", LiteralFormatUtil.format("0", PsiTypes.intType()));
    assertEquals("12", LiteralFormatUtil.format("12", PsiTypes.intType()));
    assertEquals("123", LiteralFormatUtil.format("123", PsiTypes.intType()));
    assertEquals("123L", LiteralFormatUtil.format("123L", PsiTypes.intType()));
    assertEquals("1_234", LiteralFormatUtil.format("1234", PsiTypes.intType()));
    assertEquals("123_456", LiteralFormatUtil.format("123456", PsiTypes.intType()));
    assertEquals("1_234_567_890l", LiteralFormatUtil.format("1234567890l", PsiTypes.longType()));

    assertEquals("0x", LiteralFormatUtil.format("0x", PsiTypes.intType()));
    assertEquals("0xL", LiteralFormatUtil.format("0xL", PsiTypes.longType()));
    assertEquals("0x1L", LiteralFormatUtil.format("0x1L", PsiTypes.longType()));
    assertEquals("0x1234", LiteralFormatUtil.format("0x1234", PsiTypes.intType()));
    assertEquals("0x1234l", LiteralFormatUtil.format("0x1234l", PsiTypes.longType()));
    assertEquals("0x1_abcd", LiteralFormatUtil.format("0x1abcd", PsiTypes.intType()));

    assertEquals("07", LiteralFormatUtil.format("07", PsiTypes.intType()));
    assertEquals("0_777", LiteralFormatUtil.format("0777", PsiTypes.intType()));
    assertEquals("077_777", LiteralFormatUtil.format("077777", PsiTypes.intType()));

    assertEquals("0b", LiteralFormatUtil.format("0b", PsiTypes.intType()));
    assertEquals("0b1010", LiteralFormatUtil.format("0b1010", PsiTypes.intType()));
    assertEquals("0b0_1010", LiteralFormatUtil.format("0b01010", PsiTypes.intType()));
    assertEquals("0b1010_1010", LiteralFormatUtil.format("0b10101010", PsiTypes.intType()));
    assertEquals("0b1010_1010L", LiteralFormatUtil.format("0b10101010L", PsiTypes.longType()));
  }

  @Test
  public void testDecimalFloatingPoints() {
    assertEquals("1f", LiteralFormatUtil.format("1f", PsiTypes.floatType()));
    assertEquals("123f", LiteralFormatUtil.format("123f", PsiTypes.floatType()));
    assertEquals("1_234f", LiteralFormatUtil.format("1234f", PsiTypes.floatType()));
    assertEquals("1_234d", LiteralFormatUtil.format("1234d", PsiTypes.doubleType()));

    assertEquals("1.", LiteralFormatUtil.format("1.", PsiTypes.doubleType()));
    assertEquals("1.f", LiteralFormatUtil.format("1.f", PsiTypes.floatType()));
    assertEquals("123.", LiteralFormatUtil.format("123.", PsiTypes.doubleType()));
    assertEquals("123.f", LiteralFormatUtil.format("123.f", PsiTypes.floatType()));
    assertEquals("1_234.", LiteralFormatUtil.format("1234.", PsiTypes.doubleType()));
    assertEquals("1_234.d", LiteralFormatUtil.format("1234.d", PsiTypes.floatType()));
    assertEquals("1_234.f", LiteralFormatUtil.format("1234.f", PsiTypes.floatType()));

    assertEquals(".1", LiteralFormatUtil.format(".1", PsiTypes.doubleType()));
    assertEquals(".1f", LiteralFormatUtil.format(".1f", PsiTypes.floatType()));
    assertEquals(".123", LiteralFormatUtil.format(".123", PsiTypes.doubleType()));
    assertEquals(".123f", LiteralFormatUtil.format(".123f", PsiTypes.floatType()));
    assertEquals(".123_4", LiteralFormatUtil.format(".1234", PsiTypes.doubleType()));
    assertEquals(".123_4f", LiteralFormatUtil.format(".1234f", PsiTypes.floatType()));
    assertEquals(".123_456", LiteralFormatUtil.format(".123456", PsiTypes.doubleType()));
    assertEquals(".123_456d", LiteralFormatUtil.format(".123456d", PsiTypes.doubleType()));
    assertEquals(".123_456f", LiteralFormatUtil.format(".123456f", PsiTypes.floatType()));

    assertEquals("1.1", LiteralFormatUtil.format("1.1", PsiTypes.doubleType()));
    assertEquals("1.1f", LiteralFormatUtil.format("1.1f", PsiTypes.floatType()));
    assertEquals("123.123", LiteralFormatUtil.format("123.123", PsiTypes.doubleType()));
    assertEquals("123.123f", LiteralFormatUtil.format("123.123f", PsiTypes.floatType()));
    assertEquals("1_234.123_4", LiteralFormatUtil.format("1234.1234", PsiTypes.doubleType()));
    assertEquals("1_234.123_4f", LiteralFormatUtil.format("1234.1234f", PsiTypes.floatType()));

    assertEquals("1.1e0", LiteralFormatUtil.format("1.1e0", PsiTypes.doubleType()));
    assertEquals("1.1E0f", LiteralFormatUtil.format("1.1E0f", PsiTypes.floatType()));
    assertEquals("123.123e+123", LiteralFormatUtil.format("123.123e+123", PsiTypes.doubleType()));
    assertEquals("123.123e-123f", LiteralFormatUtil.format("123.123e-123f", PsiTypes.floatType()));
    assertEquals("1_234.123_4e1_000", LiteralFormatUtil.format("1234.1234e1000", PsiTypes.doubleType()));
    assertEquals("1_234.123_4e1_000f", LiteralFormatUtil.format("1234.1234e1000f", PsiTypes.floatType()));
  }

  @Test
  public void testHexFloatingPoints() {
    assertEquals("0xp1", LiteralFormatUtil.format("0xp1", PsiTypes.doubleType()));
    assertEquals("0xp1f", LiteralFormatUtil.format("0xp1f", PsiTypes.floatType()));

    assertEquals("0x1p1", LiteralFormatUtil.format("0x1p1", PsiTypes.doubleType()));
    assertEquals("0x1p1f", LiteralFormatUtil.format("0x1p1f", PsiTypes.floatType()));
    assertEquals("0x1234p+1", LiteralFormatUtil.format("0x1234p+1", PsiTypes.doubleType()));
    assertEquals("0x1234p-1f", LiteralFormatUtil.format("0x1234p-1f", PsiTypes.floatType()));
    assertEquals("0x1_2345p1", LiteralFormatUtil.format("0x12345p1", PsiTypes.doubleType()));
    assertEquals("0x1_2345p1f", LiteralFormatUtil.format("0x12345p1f", PsiTypes.floatType()));

    assertEquals("0x1.p1", LiteralFormatUtil.format("0x1.p1", PsiTypes.doubleType()));
    assertEquals("0x1.p1f", LiteralFormatUtil.format("0x1.p1f", PsiTypes.floatType()));
    assertEquals("0x1234.p+1", LiteralFormatUtil.format("0x1234.p+1", PsiTypes.doubleType()));
    assertEquals("0x1234.p-1f", LiteralFormatUtil.format("0x1234.p-1f", PsiTypes.floatType()));
    assertEquals("0x1_2345.p1", LiteralFormatUtil.format("0x12345.p1", PsiTypes.doubleType()));
    assertEquals("0x1_2345.p1f", LiteralFormatUtil.format("0x12345.p1f", PsiTypes.floatType()));

    assertEquals("0x.1p1", LiteralFormatUtil.format("0x.1p1", PsiTypes.doubleType()));
    assertEquals("0x.1p1f", LiteralFormatUtil.format("0x.1p1f", PsiTypes.floatType()));
    assertEquals("0x.1234p+1", LiteralFormatUtil.format("0x.1234p+1", PsiTypes.doubleType()));
    assertEquals("0x.1234p-1f", LiteralFormatUtil.format("0x.1234p-1f", PsiTypes.floatType()));
    assertEquals("0x.1234_5p1", LiteralFormatUtil.format("0x.12345p1", PsiTypes.doubleType()));
    assertEquals("0x.1234_5p1f", LiteralFormatUtil.format("0x.12345p1f", PsiTypes.floatType()));

    assertEquals("0x1.1p+1", LiteralFormatUtil.format("0x1.1p+1", PsiTypes.doubleType()));
    assertEquals("0x1.1p-1f", LiteralFormatUtil.format("0x1.1p-1f", PsiTypes.floatType()));
    assertEquals("0xabcd.1234p+100", LiteralFormatUtil.format("0xabcd.1234p+100", PsiTypes.doubleType()));
    assertEquals("0xabcd.1234P-100f", LiteralFormatUtil.format("0xabcd.1234P-100f", PsiTypes.floatType()));
    assertEquals("0xab_cdef.1234_5p+1_024", LiteralFormatUtil.format("0xabcdef.12345p+1024", PsiTypes.doubleType()));
    assertEquals("0xab_cdef.1234_5P-1_024f", LiteralFormatUtil.format("0xabcdef.12345P-1024f", PsiTypes.floatType()));
  }
}

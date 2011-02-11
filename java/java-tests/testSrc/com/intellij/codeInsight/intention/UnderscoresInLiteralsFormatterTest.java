/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import com.intellij.psi.PsiType;
import com.intellij.util.text.LiteralFormatUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnderscoresInLiteralsFormatterTest {
  @Test
  public void testIntegers() {
    assertEquals("0", LiteralFormatUtil.format("0", PsiType.INT));
    assertEquals("12", LiteralFormatUtil.format("12", PsiType.INT));
    assertEquals("123", LiteralFormatUtil.format("123", PsiType.INT));
    assertEquals("123L", LiteralFormatUtil.format("123L", PsiType.INT));
    assertEquals("1_234", LiteralFormatUtil.format("1234", PsiType.INT));
    assertEquals("123_456", LiteralFormatUtil.format("123456", PsiType.INT));
    assertEquals("1_234_567_890l", LiteralFormatUtil.format("1234567890l", PsiType.LONG));

    assertEquals("0x", LiteralFormatUtil.format("0x", PsiType.INT));
    assertEquals("0xL", LiteralFormatUtil.format("0xL", PsiType.LONG));
    assertEquals("0x1L", LiteralFormatUtil.format("0x1L", PsiType.LONG));
    assertEquals("0x1234", LiteralFormatUtil.format("0x1234", PsiType.INT));
    assertEquals("0x1234l", LiteralFormatUtil.format("0x1234l", PsiType.LONG));
    assertEquals("0x1_abcd", LiteralFormatUtil.format("0x1abcd", PsiType.INT));

    assertEquals("07", LiteralFormatUtil.format("07", PsiType.INT));
    assertEquals("0_777", LiteralFormatUtil.format("0777", PsiType.INT));
    assertEquals("077_777", LiteralFormatUtil.format("077777", PsiType.INT));

    assertEquals("0b", LiteralFormatUtil.format("0b", PsiType.INT));
    assertEquals("0b1010", LiteralFormatUtil.format("0b1010", PsiType.INT));
    assertEquals("0b0_1010", LiteralFormatUtil.format("0b01010", PsiType.INT));
    assertEquals("0b1010_1010", LiteralFormatUtil.format("0b10101010", PsiType.INT));
    assertEquals("0b1010_1010L", LiteralFormatUtil.format("0b10101010L", PsiType.LONG));
  }

  @Test
  public void testDecimalFloatingPoints() {
    assertEquals("1f", LiteralFormatUtil.format("1f", PsiType.FLOAT));
    assertEquals("123f", LiteralFormatUtil.format("123f", PsiType.FLOAT));
    assertEquals("1_234f", LiteralFormatUtil.format("1234f", PsiType.FLOAT));
    assertEquals("1_234d", LiteralFormatUtil.format("1234d", PsiType.DOUBLE));

    assertEquals("1.", LiteralFormatUtil.format("1.", PsiType.DOUBLE));
    assertEquals("1.f", LiteralFormatUtil.format("1.f", PsiType.FLOAT));
    assertEquals("123.", LiteralFormatUtil.format("123.", PsiType.DOUBLE));
    assertEquals("123.f", LiteralFormatUtil.format("123.f", PsiType.FLOAT));
    assertEquals("1_234.", LiteralFormatUtil.format("1234.", PsiType.DOUBLE));
    assertEquals("1_234.d", LiteralFormatUtil.format("1234.d", PsiType.FLOAT));
    assertEquals("1_234.f", LiteralFormatUtil.format("1234.f", PsiType.FLOAT));

    assertEquals(".1", LiteralFormatUtil.format(".1", PsiType.DOUBLE));
    assertEquals(".1f", LiteralFormatUtil.format(".1f", PsiType.FLOAT));
    assertEquals(".123", LiteralFormatUtil.format(".123", PsiType.DOUBLE));
    assertEquals(".123f", LiteralFormatUtil.format(".123f", PsiType.FLOAT));
    assertEquals(".123_4", LiteralFormatUtil.format(".1234", PsiType.DOUBLE));
    assertEquals(".123_4f", LiteralFormatUtil.format(".1234f", PsiType.FLOAT));
    assertEquals(".123_456", LiteralFormatUtil.format(".123456", PsiType.DOUBLE));
    assertEquals(".123_456d", LiteralFormatUtil.format(".123456d", PsiType.DOUBLE));
    assertEquals(".123_456f", LiteralFormatUtil.format(".123456f", PsiType.FLOAT));

    assertEquals("1.1", LiteralFormatUtil.format("1.1", PsiType.DOUBLE));
    assertEquals("1.1f", LiteralFormatUtil.format("1.1f", PsiType.FLOAT));
    assertEquals("123.123", LiteralFormatUtil.format("123.123", PsiType.DOUBLE));
    assertEquals("123.123f", LiteralFormatUtil.format("123.123f", PsiType.FLOAT));
    assertEquals("1_234.123_4", LiteralFormatUtil.format("1234.1234", PsiType.DOUBLE));
    assertEquals("1_234.123_4f", LiteralFormatUtil.format("1234.1234f", PsiType.FLOAT));

    assertEquals("1.1e0", LiteralFormatUtil.format("1.1e0", PsiType.DOUBLE));
    assertEquals("1.1E0f", LiteralFormatUtil.format("1.1E0f", PsiType.FLOAT));
    assertEquals("123.123e+123", LiteralFormatUtil.format("123.123e+123", PsiType.DOUBLE));
    assertEquals("123.123e-123f", LiteralFormatUtil.format("123.123e-123f", PsiType.FLOAT));
    assertEquals("1_234.123_4e1_000", LiteralFormatUtil.format("1234.1234e1000", PsiType.DOUBLE));
    assertEquals("1_234.123_4e1_000f", LiteralFormatUtil.format("1234.1234e1000f", PsiType.FLOAT));
  }

  @Test
  public void testHexFloatingPoints() {
    assertEquals("0xp1", LiteralFormatUtil.format("0xp1", PsiType.DOUBLE));
    assertEquals("0xp1f", LiteralFormatUtil.format("0xp1f", PsiType.FLOAT));

    assertEquals("0x1p1", LiteralFormatUtil.format("0x1p1", PsiType.DOUBLE));
    assertEquals("0x1p1f", LiteralFormatUtil.format("0x1p1f", PsiType.FLOAT));
    assertEquals("0x1234p+1", LiteralFormatUtil.format("0x1234p+1", PsiType.DOUBLE));
    assertEquals("0x1234p-1f", LiteralFormatUtil.format("0x1234p-1f", PsiType.FLOAT));
    assertEquals("0x1_2345p1", LiteralFormatUtil.format("0x12345p1", PsiType.DOUBLE));
    assertEquals("0x1_2345p1f", LiteralFormatUtil.format("0x12345p1f", PsiType.FLOAT));

    assertEquals("0x1.p1", LiteralFormatUtil.format("0x1.p1", PsiType.DOUBLE));
    assertEquals("0x1.p1f", LiteralFormatUtil.format("0x1.p1f", PsiType.FLOAT));
    assertEquals("0x1234.p+1", LiteralFormatUtil.format("0x1234.p+1", PsiType.DOUBLE));
    assertEquals("0x1234.p-1f", LiteralFormatUtil.format("0x1234.p-1f", PsiType.FLOAT));
    assertEquals("0x1_2345.p1", LiteralFormatUtil.format("0x12345.p1", PsiType.DOUBLE));
    assertEquals("0x1_2345.p1f", LiteralFormatUtil.format("0x12345.p1f", PsiType.FLOAT));

    assertEquals("0x.1p1", LiteralFormatUtil.format("0x.1p1", PsiType.DOUBLE));
    assertEquals("0x.1p1f", LiteralFormatUtil.format("0x.1p1f", PsiType.FLOAT));
    assertEquals("0x.1234p+1", LiteralFormatUtil.format("0x.1234p+1", PsiType.DOUBLE));
    assertEquals("0x.1234p-1f", LiteralFormatUtil.format("0x.1234p-1f", PsiType.FLOAT));
    assertEquals("0x.1234_5p1", LiteralFormatUtil.format("0x.12345p1", PsiType.DOUBLE));
    assertEquals("0x.1234_5p1f", LiteralFormatUtil.format("0x.12345p1f", PsiType.FLOAT));

    assertEquals("0x1.1p+1", LiteralFormatUtil.format("0x1.1p+1", PsiType.DOUBLE));
    assertEquals("0x1.1p-1f", LiteralFormatUtil.format("0x1.1p-1f", PsiType.FLOAT));
    assertEquals("0xabcd.1234p+100", LiteralFormatUtil.format("0xabcd.1234p+100", PsiType.DOUBLE));
    assertEquals("0xabcd.1234P-100f", LiteralFormatUtil.format("0xabcd.1234P-100f", PsiType.FLOAT));
    assertEquals("0xab_cdef.1234_5p+1_024", LiteralFormatUtil.format("0xabcdef.12345p+1024", PsiType.DOUBLE));
    assertEquals("0xab_cdef.1234_5P-1_024f", LiteralFormatUtil.format("0xabcdef.12345P-1024f", PsiType.FLOAT));
  }
}

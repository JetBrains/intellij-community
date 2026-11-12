// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.integer;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LangBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class JavaConvertNumericBaseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "integer", "Convert number to…");

  @Test
  public void testDecToHex1() { doTestWithChooser("Hex"); }
  @Test
  public void testDecToHex2() { doTestWithChooser("Hex"); }
  @Test
  public void testDecToHex3() { doTestWithChooser("Hex"); }
  @Test
  public void testDecToHex4() { doTestWithChooser("Hex"); }
  @Test
  public void testOctToHex1() { doTestWithChooser("Hex"); }
  @Test
  public void testOctToHex2() { doTestWithChooser("Hex"); }
  @Test
  public void testBinToHex1() { doTestWithChooser("Hex"); }
  @Test
  public void testBinToHex2() { doTestWithChooser("Hex"); }
  @Test
  public void testDecToOct1() { doTestWithChooser("Octal"); }
  @Test
  public void testDecToOct2() { doTestWithChooser("Octal"); }
  @Test
  public void testHexToOct1() { doTestWithChooser("Octal"); }
  @Test
  public void testHexToOct2() { doTestWithChooser("Octal"); }
  @Test
  public void testBinToOct1() { doTestWithChooser("Octal"); }
  @Test
  public void testBinToOct2() { doTestWithChooser("Octal"); }
  @Test
  public void testRedOctal() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testHexToDec1() { doTestWithChooser("Decimal"); }
  @Test
  public void testHexToDec2() { doTestWithChooser("Decimal"); }
  @Test
  public void testHexToDec3() { doTestWithChooser("Decimal"); }
  @Test
  public void testHexToDec4() { doTestWithChooser("Decimal"); }
  @Test
  public void testOctToDec1() { doTestWithChooser("Decimal"); }
  @Test
  public void testOctToDec2() { doTestWithChooser("Decimal"); }
  @Test
  public void testBinToDec1() { doTestWithChooser("Decimal"); }
  @Test
  public void testBinToDec2() { doTestWithChooser("Decimal"); }
  @Test
  public void testDecToBin1() { doTestWithChooser("Binary"); }
  @Test
  public void testDecToBin2() { doTestWithChooser("Binary"); }
  @Test
  public void testHexToBin1() { doTestWithChooser("Binary"); }
  @Test
  public void testHexToBin2() { doTestWithChooser("Binary"); }
  @Test
  public void testOctToBin1() { doTestWithChooser("Binary"); }
  @Test
  public void testOctToBin2() { doTestWithChooser("Binary"); }
  
  @Test
  public void testPreview() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.configureByText("Test.java", "class Test {int x = 123<caret>;}");
    IntentionAction action = myFixture.findSingleIntention(LangBundle.message("intention.name.convert.number.to"));
    myFixture.checkIntentionPreviewHtml(action, "Convert number to hex, binary, or octal");
  }

  private void doTestWithChooser(String selectedOption) {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(selectedOption) + " \\(.*\\)"));
    ipp.get().doTest();
  }
}

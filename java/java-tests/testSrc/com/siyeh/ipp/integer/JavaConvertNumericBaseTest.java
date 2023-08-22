// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.integer;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LangBundle;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.ipp.IPPTestCase;

import java.util.regex.Pattern;

public class JavaConvertNumericBaseTest extends IPPTestCase {
  public void testDecToHex1() { doTestWithChooser("Hex"); }
  public void testDecToHex2() { doTestWithChooser("Hex"); }
  public void testDecToHex3() { doTestWithChooser("Hex"); }
  public void testDecToHex4() { doTestWithChooser("Hex"); }
  public void testOctToHex1() { doTestWithChooser("Hex"); }
  public void testOctToHex2() { doTestWithChooser("Hex"); }
  public void testBinToHex1() { doTestWithChooser("Hex"); }
  public void testBinToHex2() { doTestWithChooser("Hex"); }
  public void testDecToOct1() { doTestWithChooser("Octal"); }
  public void testDecToOct2() { doTestWithChooser("Octal"); }
  public void testHexToOct1() { doTestWithChooser("Octal"); }
  public void testHexToOct2() { doTestWithChooser("Octal"); }
  public void testBinToOct1() { doTestWithChooser("Octal"); }
  public void testBinToOct2() { doTestWithChooser("Octal"); }
  public void testRedOctal() { assertIntentionNotAvailable(); }
  public void testHexToDec1() { doTestWithChooser("Decimal"); }
  public void testHexToDec2() { doTestWithChooser("Decimal"); }
  public void testHexToDec3() { doTestWithChooser("Decimal"); }
  public void testHexToDec4() { doTestWithChooser("Decimal"); }
  public void testOctToDec1() { doTestWithChooser("Decimal"); }
  public void testOctToDec2() { doTestWithChooser("Decimal"); }
  public void testBinToDec1() { doTestWithChooser("Decimal"); }
  public void testBinToDec2() { doTestWithChooser("Decimal"); }
  public void testDecToBin1() { doTestWithChooser("Binary"); }
  public void testDecToBin2() { doTestWithChooser("Binary"); }
  public void testHexToBin1() { doTestWithChooser("Binary"); }
  public void testHexToBin2() { doTestWithChooser("Binary"); }
  public void testOctToBin1() { doTestWithChooser("Binary"); }
  public void testOctToBin2() { doTestWithChooser("Binary"); }
  
  public void testPreview() {
    myFixture.configureByText("Test.java", "class Test {int x = 123<caret>;}");
    IntentionAction action = myFixture.findSingleIntention(LangBundle.message("intention.name.convert.number.to"));
    myFixture.checkIntentionPreviewHtml(action, "Convert number to hex, binary, or octal");
  }

  private void doTestWithChooser(String selectedOption) {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(selectedOption) + " \\(.*\\)"));
    doTest();
  }
  
  @Override
  protected String getIntentionName() {
    return "Convert number to…";
  }

  @Override
  protected String getRelativePath() {
    return "integer";
  }
}

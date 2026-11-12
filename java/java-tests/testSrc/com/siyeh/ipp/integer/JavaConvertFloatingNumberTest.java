// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.integer;

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
public class JavaConvertFloatingNumberTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "float", "Convert number to…");

  @Test
  public void testToPlain() { doTestWithChooser("Plain format"); }
  @Test
  public void testToPlainShort() { doTestWithChooser("Plain format"); }
  @Test
  public void testToPlainLong() { doTestWithChooser("Plain format"); }
  @Test
  public void testToHex() { doTestWithChooser("Hex"); }
  @Test
  public void testNegatedFloatToPlain() { doTestWithChooser("Plain format"); }
  @Test
  public void testWithUnderscoresToPlain() { doTestWithChooser("Plain format"); }
  @Test
  public void testNoTrailingZeros() { doTestWithChooser("Plain format"); }
  @Test
  public void testToSci() { doTestWithChooser("Scientific format"); }
  @Test
  public void testNegatedFloatToSci() { doTestWithChooser("Scientific format"); }
  @Test
  public void testWithUnderscoresToSci() { doTestWithChooser("Scientific format"); }

  private void doTestWithChooser(String selectedOption) {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(selectedOption) + " \\(.*\\)"));
    ipp.get().doTest();
  }
}

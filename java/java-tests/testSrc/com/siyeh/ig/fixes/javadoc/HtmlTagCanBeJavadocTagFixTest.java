package com.siyeh.ig.fixes.javadoc;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.javadoc.HtmlTagCanBeJavadocTagInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class HtmlTagCanBeJavadocTagFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new HtmlTagCanBeJavadocTagInspection()), "javadoc/html_tag_can_be_javadoc_tag",
                        CommonQuickFixBundle.message("fix.replace.with.x", "{@code ...}"));

  @Test
  public void testBraces() { ig.get().doTest(); }
  @Test
  public void testBraces2() { ig.get().doTest(); }
  @Test
  public void testSecond() { ig.get().doTest(); }
  @Test
  public void testMultiline() { ig.get().doTest(); }
  @Test
  public void testLt() { ig.get().doTest(); }
}
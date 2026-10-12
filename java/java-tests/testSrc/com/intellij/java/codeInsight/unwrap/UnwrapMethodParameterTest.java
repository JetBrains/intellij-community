// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class UnwrapMethodParameterTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter, int option) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, option);
  }

  private void assertOptions(String code, String... expectedOptions) {
    UnwrapTestUtil.assertOptions(codeInsightFixture.get(), code, expectedOptions);
  }

  @Test
  public void testBasic() {
    assertOptions("foo(ba<caret>r());",
                  "Unwrap 'bar()'");

    assertUnwrapped("foo(ba<caret>r());",
                    "ba<caret>r();");
  }

  @Test
  public void testSeveralParameters() {
    assertUnwrapped("foo(bar(), ba<caret>z());",
                    "ba<caret>z();");
  }

  @Test
  public void testInnerCalls() {
    assertOptions("foo(bar(ba<caret>z()));",
                  "Unwrap 'baz()'",
                  "Unwrap 'bar(baz())'");

    assertUnwrapped("foo(bar(ba<caret>z()));",
                    "foo(ba<caret>z());",
                    0);

    assertUnwrapped("foo(bar(ba<caret>z()));",
                    "bar(ba<caret>z());",
                    1);
  }

  @Test
  public void testInstantiationParameter() {
    assertOptions("foo(new Stri<caret>ng());",
                  "Unwrap 'new String()'");
    assertUnwrapped("foo(new Stri<caret>ng());",
                    "new Stri<caret>ng();");
  }

  @Test
  public void testSimpleParameter() {
    assertOptions("foo(b<caret>ar);",
                  "Unwrap 'bar'");
    assertUnwrapped("foo(b<caret>ar);",
                    "b<caret>ar;");
  }

  @Test
  public void testFromAssignment() {
    assertOptions("int i = foo(b<caret>ar());",
                  "Unwrap 'bar()'");
    assertUnwrapped("int i = foo(b<caret>ar());",
                    "int i = b<caret>ar();");
  }

  @Test
  public void testFromTopLevelNew() {
    assertOptions("Foo f = new Fo<caret>o(bar());",
                  "Unwrap 'Foo'");
    assertUnwrapped("Foo f = new Fo<caret>o(bar());",
                    "Foo f = bar()<caret>;");
  }

  @Test
  public void testFromTopLevel() {
    assertOptions("int f = fo<caret>o(bar());",
                  "Unwrap 'foo'");
    assertUnwrapped("int f = fo<caret>o(bar());",
                    "int f = bar()<caret>;");
  }

  @Test
  public void testBeforeRightParenth() {
    assertOptions("int f = foo(bar(\"path\"<caret>));",
                  "Unwrap '\"path\"'", "Unwrap 'bar(\"path\")'");
    assertUnwrapped("int f = foo(bar(\"path\"<caret>));",
                    "int f = foo(\"path\"<caret>);");
  }

  @Test
  public void testHighlightingOfTheExtractedFragment() {
    assertOptions("foo(bar.st<caret>r);",
                  "Unwrap 'bar.str'");
    assertUnwrapped("foo(bar.st<caret>r);",
                    "bar.str;");
    final RangeHighlighter[] highlighters = codeInsightFixture.get().getEditor().getMarkupModel().getAllHighlighters();
    assertEquals(1, highlighters.length);
    assertEquals(42, highlighters[0].getStartOffset());
    assertEquals(49, highlighters[0].getEndOffset());
  }
}
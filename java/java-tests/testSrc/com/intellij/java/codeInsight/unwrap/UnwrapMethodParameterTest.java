// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;
import com.intellij.openapi.editor.markup.RangeHighlighter;

public class UnwrapMethodParameterTest extends UnwrapTestCase {
  public void testBasic() {
    assertOptions("foo(ba<caret>r());",
                  "Unwrap 'bar()'");

    assertUnwrapped("foo(ba<caret>r());",
                    "ba<caret>r();");
  }

  public void testSeveralParameters() {
    assertUnwrapped("foo(bar(), ba<caret>z());",
                    "ba<caret>z();");
  }

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

  public void testInstantiationParameter() {
    assertOptions("foo(new Stri<caret>ng());",
                  "Unwrap 'new String()'");
    assertUnwrapped("foo(new Stri<caret>ng());",
                    "new Stri<caret>ng();");
  }

  public void testSimpleParameter() {
    assertOptions("foo(b<caret>ar);",
                  "Unwrap 'bar'");
    assertUnwrapped("foo(b<caret>ar);",
                    "b<caret>ar;");
  }

  public void testFromAssignment() {
    assertOptions("int i = foo(b<caret>ar());",
                  "Unwrap 'bar()'");
    assertUnwrapped("int i = foo(b<caret>ar());",
                    "int i = b<caret>ar();");
  }

  public void testFromTopLevelNew() {
    assertOptions("Foo f = new Fo<caret>o(bar());",
                  "Unwrap 'Foo'");
    assertUnwrapped("Foo f = new Fo<caret>o(bar());",
                    "Foo f = bar()<caret>;");
  }

  public void testFromTopLevel() {
    assertOptions("int f = fo<caret>o(bar());",
                  "Unwrap 'foo'");
    assertUnwrapped("int f = fo<caret>o(bar());",
                    "int f = bar()<caret>;");
  }

  public void testBeforeRightParenth() {
    assertOptions("int f = foo(bar(\"path\"<caret>));",
                  "Unwrap '\"path\"'", "Unwrap 'bar(\"path\")'");
    assertUnwrapped("int f = foo(bar(\"path\"<caret>));",
                    "int f = foo(\"path\"<caret>);");
  }

  public void testHighlightingOfTheExtractedFragment() {
    assertOptions("foo(bar.st<caret>r);",
                  "Unwrap 'bar.str'");
    assertUnwrapped("foo(bar.st<caret>r);",
                    "bar.str;");
    final RangeHighlighter[] highlighters = myEditor.getMarkupModel().getAllHighlighters();
    assertSize(1, highlighters);
    assertEquals(42, highlighters[0].getStartOffset());
    assertEquals(49, highlighters[0].getEndOffset());
  }
}
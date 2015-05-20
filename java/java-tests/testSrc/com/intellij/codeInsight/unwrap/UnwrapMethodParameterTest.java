package com.intellij.codeInsight.unwrap;

public class UnwrapMethodParameterTest extends UnwrapTestCase {
  public void testBasic() throws Exception {
    assertOptions("foo(ba<caret>r());",
                  "Unwrap 'bar()'");

    assertUnwrapped("foo(ba<caret>r());",
                    "ba<caret>r();");
  }

  public void testSeveralParameters() throws Exception {
    assertUnwrapped("foo(bar(), ba<caret>z());",
                    "ba<caret>z();");
  }

  public void testInnerCalls() throws Exception {
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

  public void testInstantiationParameter() throws Exception {
    assertOptions("foo(new Stri<caret>ng());",
                  "Unwrap 'new String()'");
    assertUnwrapped("foo(new Stri<caret>ng());",
                    "new Stri<caret>ng();");
  }

  public void testSimpleParameter() throws Exception {
    assertOptions("foo(b<caret>ar);",
                  "Unwrap 'bar'");
    assertUnwrapped("foo(b<caret>ar);",
                    "b<caret>ar;");
  }

  public void testFromAssignment() throws Exception {
    assertOptions("int i = foo(b<caret>ar());",
                  "Unwrap 'bar()'");
    assertUnwrapped("int i = foo(b<caret>ar());",
                    "int i = b<caret>ar();");
  }

  public void testFromTopLevelNew() throws Exception {
    assertOptions("Foo f = new Fo<caret>o(bar());",
                  "Unwrap 'Foo'");
    assertUnwrapped("Foo f = new Fo<caret>o(bar());",
                    "Foo f = bar()<caret>;");
  }

  public void testFromTopLevel() throws Exception {
    assertOptions("int f = fo<caret>o(bar());",
                  "Unwrap 'foo'");
    assertUnwrapped("int f = fo<caret>o(bar());",
                    "int f = bar()<caret>;");
  }

  public void testBeforeRightParenth() throws Exception {
    assertOptions("int f = foo(bar(\"path\"<caret>));",
                  "Unwrap '\"path\"'", "Unwrap 'bar(\"path\")'");
    assertUnwrapped("int f = foo(bar(\"path\"<caret>));",
                    "int f = foo(\"path\"<caret>);");
  }
}
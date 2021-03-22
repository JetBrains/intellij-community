package test;

import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import java.util.function.Function;

public class TestOnlyTest {
  @TestOnly
  TestOnlyTest() { }

  @TestOnly
  static String someString(String someStr) { return someStr + "Foo"; }

  @TestOnly
  @VisibleForTesting
  static String <warning descr="@VisibleForTesting makes little sense on @TestOnly code">doubleAnn</warning>() { return "Foo"; }

  static class Bar {
    @TestOnly
    int aField = 0;

    @TestOnly
    void aMethod() { }
  }

  public static void main(String[] args) {
    TestOnlyTest foo = <warning descr="Test-only class is referenced in production code">new TestOnlyTest()</warning>;
    Bar bar = new Bar();
    int aField = <warning descr="Test-only field is referenced in production code">bar.aField</warning>;
    <warning descr="Test-only method is called in production code">bar.aMethod()</warning>;
    Function<String, String> methodRef = <warning descr="Test-only method is called in production code">TestOnlyTest::someString</warning>;
  }
}


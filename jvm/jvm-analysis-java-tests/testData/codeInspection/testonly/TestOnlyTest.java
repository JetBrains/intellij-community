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
  @<warning descr="@VisibleForTesting makes little sense on @TestOnly code">VisibleForTesting</warning>
  static String doubleAnn() { return "Foo"; }

  static class Bar {
    @TestOnly
    int aField = 0;

    @TestOnly
    void aMethod() { }
  }

  public static void main(String[] args) {
    TestOnlyTest foo = new <warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>();
    Bar bar = new Bar();
    int aField = bar.<warning descr="Test-only field is referenced in production code">aField</warning>;
    bar.<warning descr="Test-only method is called in production code">aMethod</warning>();
    Function<String, String> methodRef = TestOnlyTest::<warning descr="Test-only method is called in production code">someString</warning>;
  }

  @TestOnly
  public static void testOnly() {
    TestOnlyTest foo = new TestOnlyTest();
    Bar bar = new Bar();
    int aField = bar.aField;
    bar.aMethod();
    Function<String, String> methodRef = TestOnlyTest::someString;
  }
}


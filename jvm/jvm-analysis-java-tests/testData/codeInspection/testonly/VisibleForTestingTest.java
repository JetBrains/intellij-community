package test;

import org.jetbrains.annotations.VisibleForTesting;
import test.VisibleForTestingTestApi;

public class VisibleForTestingTest {
  @VisibleForTesting
  static int fooBar = 0;

  public static void main(String[] args) {
    System.out.println(fooBar);
    System.out.println(<warning descr="Test-only field is referenced in production code">VisibleForTestingTestApi.foo</warning>);
    <warning descr="Test-only method is called in production code">VisibleForTestingTestApi.bar()</warning>;
  }
}


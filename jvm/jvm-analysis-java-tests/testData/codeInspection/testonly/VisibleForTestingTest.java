package test;

import org.jetbrains.annotations.VisibleForTesting;
import test.VisibleForTestingTestApi;

public class VisibleForTestingTest {
  @VisibleForTesting
  static int fooBar = 0;

  public static void main(String[] args) {
    System.out.println(fooBar);
    System.out.println(VisibleForTestingTestApi.<warning descr="Test-only field is referenced in production code">foo</warning>);
    VisibleForTestingTestApi.<warning descr="Test-only method is called in production code">bar</warning>();
  }
}


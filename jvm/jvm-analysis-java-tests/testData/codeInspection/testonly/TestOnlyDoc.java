
package test;

import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import java.util.function.Function;

public class TestOnlyDoc {
  @TestOnly
  TestOnlyDoc() { }

  @TestOnly
  static String testMethod() { return "Foo"; }

  /**
   * {@link #TestOnlyTest()}
   * {@link #TestOnlyTest#testMethod()}
   * {@link #testMethod()}
   */
  public static void docced() { }
}


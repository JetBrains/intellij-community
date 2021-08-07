package test;

import org.jetbrains.annotations.VisibleForTesting;

public class VisibleForTestingTestApi {
  @VisibleForTesting
  static int foo = x;

  @VisibleForTesting
  static void bar() { }
}


package com.example.sqlinjection;

import org.checkerframework.checker.tainting.qual.Untainted;

public class CleanQualifier {

  public static void test(CleanQualifier mustBeSafe) {
    mustBeSafe.setSafe(true);
    sink(mustBeSafe);
  }
  public static void test2(CleanQualifier mustBeSafe) {
    mustBeSafe.setSafe(false);
    sink(<warning descr="Unknown string is used as safe parameter">mustBeSafe</warning>);
  }
  public static void test3(CleanQualifier mustBeSafe) {
    sink(<warning descr="Unknown string is used as safe parameter">mustBeSafe</warning>);
  }

  private void setSafe(boolean b) {

  }

  public static void sink(@Untainted CleanQualifier t) {

  }
}

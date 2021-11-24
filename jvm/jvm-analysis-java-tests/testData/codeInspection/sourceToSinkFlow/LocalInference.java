package org.checkerframework.checker.tainting.qual;

public class LocalInference {

    void simpleInit() {
      String s1 = source();
      String s = s1;
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void concatInit() {
      String s1 = "foo" + source() + "bar";
      String s = "foo" + s1 + "bar";
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void recursive() {
      String s1 = source();
      String s = s1;
      s1 = s;
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void ternary(boolean b) {
      String s1 = b ? source() : "";
      String s = s1;
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void nestedTernary(boolean b, boolean c) {
      String s1 = b ? c ? "" : (source()) : "";
      String s = s1;
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void blocks() {
      String s1;
      {
        s1 = source();
        {
          String s = s1;
          sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
        }
      }
    }

    void transitive(boolean b) {
      String s2;
      {
        s2 = b ? b ? (source() + source()) : "" : "";
      }
      String s1 = ((s2) + (safe()));
      String s = s1;
      sink(<warning descr="Unsafe string is used as safe parameter">s</warning>);
    }

    void transitiveRecursive(boolean b) {
      String s = "";
      String s1 = s;
      String s2 = s1 + foo();
      String s3 = s2;
      if (b) s = s3;
      sink(<warning descr="Unknown string is used as safe parameter">s</warning>);
    }

    String foo() {
      return "";
    }

    @Untainted
    String safe() {
      return "safe";
    }

    @Tainted
    String source() {
      return "tainted";
    }

    void sink(@Untainted String s1) {}
}

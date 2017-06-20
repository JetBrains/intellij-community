class C {
  static boolean b = System.getProperty("foo") != null;

  static class C1 {
    { <warning descr="The value \"a\" assigned to 's' is never used">s</warning> = "a"; }
    String s = "b";
  }
  static class C2 {
    String s = <warning descr="Variable 's' initializer '\"b\"' is redundant">"b"</warning>;
    { s = "c"; }
  }
  static class C3 {
    { <warning descr="The value \"a\" assigned to 's' is never used">s</warning> = "a"; }
    String s;
    { s = "c"; }
  }
  static class C4 {
    { if (b) <warning descr="The value \"a\" assigned to 's' is never used">s</warning> = "a"; }
    String s = "b";
  }
  static class C5 {
    String s = "b";
    { if (b) s = "c"; }
  }
  static class C6 {
    String s = <warning descr="Variable 's' initializer '\"b\"' is redundant">"b"</warning>;
    { if (b) s = "c"; else s = "d"; }
  }
  static class C7 {
    String s;
    { s = "c"; }
  }
  static class C8 {
    { s = "a"; }
    String s;
  }
  static class C9 {
    String s;
    {
      <warning descr="The value \"b\" assigned to 's' is never used">s</warning> = "b";
      if (b) <warning descr="The value \"c\" assigned to 's' is never used">s</warning> = "c";
    }
    { s = "d"; }
  }

  static class S1 {
    static { <warning descr="The value \"a\" assigned to 's' is never used">s</warning> = "a"; }
    static String s = "b";
  }
  static class S2 {
    static String s = <warning descr="Variable 's' initializer '\"b\"' is redundant">"b"</warning>;
    static { s = "c"; }
  }
  static class S3 {
    static { <warning descr="The value \"a\" assigned to 's' is never used">s</warning> = "a"; }
    static String s;
    static { s = "c"; }
  }
  static class S4 {
    static { if (b) <warning descr="The value \"a\" assigned to 's' is never used">s</warning> = "a"; }
    static String s = "b";
  }
  static class S5 {
    static String s = "b";
    static { if (b) s = "c"; }
  }
  static class S6 {
    static String s = <warning descr="Variable 's' initializer '\"b\"' is redundant">"b"</warning>;
    static { if (b) s = "c"; else s = "d"; }
  }
  static class S7 {
    static String s;
    static { s = "c"; }
  }
  static class S8 {
    static { s = "a"; }
    static String s;
  }
  static class S9 {
    static String s;
    static {
      <warning descr="The value \"b\" assigned to 's' is never used">s</warning> = "b";
      if (b) <warning descr="The value \"c\" assigned to 's' is never used">s</warning> = "c";
    }
    static { s = "d"; }
  }
  static class S10 {
    static String s = "a";
    { s = "b"; }
  }
}
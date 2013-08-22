class X {

  int foo(String d1, String d2) {
    if(d1 == null | d2 == null)
      return 0;
    return d1.compareTo(d2);

  }
  void foo2(String d1, String d2) {
    if(<warning descr="Condition 'd1 == null & d1 != null' is always 'true'">d1 == null & d1 != null</warning>)
      System.out.println("impossible");

  }
  void foo3(String d1, String d2) {
    if(d1 == null | <warning descr="Method invocation 'd1.compareTo(d2)' may produce 'java.lang.NullPointerException'">d1.compareTo(d2)</warning> > 0)
      System.out.println("impossible");
  }
}


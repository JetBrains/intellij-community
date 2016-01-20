import org.jetbrains.annotations.NotNull;

class X {

  int foo(String d1, String d2) {
    if(d1 == null | d2 == null)
      return 0;
    return d1.compareTo(d2);

  }
  void foo2(String d1, String d2) {
    if(<warning descr="Condition 'd1 == null & d1 != null' is always 'false'">d1 == null & d1 != null</warning>)
      System.out.println("impossible");

  }
  void foo3(String d1, String d2) {
    if(d1 == null | d1.<warning descr="Method invocation 'compareTo' may produce 'java.lang.NullPointerException'">compareTo</warning>(d2) > 0)
      System.out.println("impossible");
  }
}

class Doo {

  void zoo(@NotNull Object t, @NotNull Object s) {
  }


  private void goo(Object t, Object t2) {
    if (t != null & t2 != null) {
      zoo(t, t2);
    }
  }

}

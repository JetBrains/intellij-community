class X {
  void expressions(Object obj) {
    if (obj instanceof String <warning descr="Pattern variable 's' is never used">s</warning>) {
      
    }
    if (obj instanceof Integer integer) {
      System.out.println(integer);
    }
  }
}
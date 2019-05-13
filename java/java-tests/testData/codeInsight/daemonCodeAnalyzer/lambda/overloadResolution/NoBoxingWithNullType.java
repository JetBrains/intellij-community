class Test {
  private void <warning descr="Private method 'foo(java.lang.String, java.lang.Object)' is never used">foo</warning>(String s, Object o) {
    System.out.println(s + "; o:" + o);
  }

  private void foo(String s, boolean b) {
    System.out.println(s + "; b:" + b);
  }
  
  {
    foo(null, false);
  }
}
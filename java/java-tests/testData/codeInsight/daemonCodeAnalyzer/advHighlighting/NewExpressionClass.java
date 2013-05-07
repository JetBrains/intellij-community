class Test {
  void foo(Object obj) {
    new <error descr="Cannot find symbol obj">obj</error>();
    new <error descr="Cannot find symbol java">java</error>();
    new java.lang.Object();
    new Object();
  }
}
class Test {
  void foo(Object obj) {
    new <error descr="Cannot resolve symbol 'obj'">obj</error>();
    new <error descr="Cannot resolve symbol 'java'">java</error>();
    new java.lang.Object();
    new Object();
  }
}
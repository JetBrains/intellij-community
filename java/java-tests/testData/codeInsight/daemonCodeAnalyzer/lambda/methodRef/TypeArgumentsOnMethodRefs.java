class Test<T> {
  void foo(String p) {}
  <U> void foo1(String p) {}
  static void foo2(String p) {}
  static <U> void foo3(String p) {}
  void test() {
    Test test = new Test<String>();
    BlahBlah<String> blahBlah = test::<String>foo;
    BlahBlah<String> blahBlah1 = test::<String>foo1;
    <error descr="Incompatible types. Found: '<method reference>', required: 'BlahBlah<java.lang.String>'">BlahBlah<String> blahBlah2 = test::<String>foo2;</error>
    <error descr="Incompatible types. Found: '<method reference>', required: 'BlahBlah<java.lang.String>'">BlahBlah<String> blahBlah3 = test::<String>foo3;</error>
  }
}

interface BlahBlah<T> {
  void bar(T i);
}
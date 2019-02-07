class Test<T> {
  void foo(String p) {}
  <U> void foo1(String p) {}
  static void foo2(String p) {}
  static <U> void foo3(String p) {}
  void test() {
    Test test = new Test<String>();
    BlahBlah<String> blahBlah = test::<String>foo;
    BlahBlah<String> blahBlah1 = test::<String>foo1;
    BlahBlah<String> blahBlah2 = <error descr="Static method referenced through non-static qualifier">test::<String>foo2</error>;
    BlahBlah<String> blahBlah3 = <error descr="Static method referenced through non-static qualifier">test::<String>foo3</error>;
    BlahBlah<String> blahBlah4 = Test::<error descr="Wrong number of type arguments: 2; required: 1"><String, String></error>foo3;
    BlahBlah<String> blahBlah5 = test::<String, String>foo1;
  }
}

interface BlahBlah<T> {
  void bar(T i);
}
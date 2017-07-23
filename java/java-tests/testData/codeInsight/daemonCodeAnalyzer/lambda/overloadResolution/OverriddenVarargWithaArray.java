interface TypeA {
  void test(String... arg);
}

interface TypeB extends TypeA {
  void test(String[] arg);
}

class Test {
  void foo(final TypeB typeB) {
    typeB.test<error descr="'test(java.lang.String[])' in 'TypeB' cannot be applied to '(java.lang.String, java.lang.String)'">("a", "b")</error>;
    typeB.test<error descr="'test(java.lang.String[])' in 'TypeB' cannot be applied to '(java.lang.String)'">("a")</error>;
    typeB.test<error descr="'test(java.lang.String[])' in 'TypeB' cannot be applied to '()'">()</error>;
  }
}

class Test2 {
  void foo(String s, Integer p) {}

  <T> T bar(Class<T> c) {
    return null;
  }

  {
    foo (bar(String.class), <error descr="'foo(java.lang.String, java.lang.Integer)' in 'Test2' cannot be applied to '(T, java.lang.String)'">""</error>);
  }
}

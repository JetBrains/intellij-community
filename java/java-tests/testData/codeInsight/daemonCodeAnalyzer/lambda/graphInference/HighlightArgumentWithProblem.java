
class Test2 {
  void foo(String s, Integer p) {}

  <T> T bar(Class<T> c) {
    return null;
  }

  {
    foo (bar<error descr="'bar(java.lang.Class<T>)' in 'Test2' cannot be applied to '(java.lang.Class<java.lang.String>)'">(String.class)</error>, "");
  }
}

class MyTest {
  <T> T foo(T t) {
    return t;
  }

  void m(int i) {
    String s = foo(switch (i) {default -> "str";});
    String s1 = <error descr="Incompatible types. Required String but 'foo' was inferred to T:
no instance(s) of type variable(s)  exist so that Object conforms to String">foo(switch (i) {case 1 -> new Object(); default -> "str";});</error>
  }
}
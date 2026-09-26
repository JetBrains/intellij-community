interface X  {
  void m(Integer i);
}

interface Y<T> {
  void m(T t);
}

class Test {
  {
    ((X & Y<Integer>) <error descr="Multiple non-overriding abstract methods found in X & Y<Integer>">(x) -> {}</error>).m<error descr="Ambiguous method call: both 'X.m(Integer)' and 'Y.m(Integer)' match">(1)</error>;
  }
}
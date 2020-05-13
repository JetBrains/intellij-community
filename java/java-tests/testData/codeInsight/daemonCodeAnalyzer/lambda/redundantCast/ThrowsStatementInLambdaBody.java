interface SAM<T, R extends Throwable> {
  void m(T t) throws R;
}

class MyTest {
  void f(E1 e1) {
    SAM<String, E1> s1 = s -> {
      if (s.length() > 0) throw (<warning descr="Casting '(E2)e1' to 'E1' is redundant">E1</warning>) (<warning descr="Casting 'e1' to 'E2' is redundant">E2</warning>) e1;
      throw (<warning descr="Casting '(E1)e1' to 'E2' is redundant">E2</warning>) (<warning descr="Casting 'e1' to 'E1' is redundant">E1</warning>)e1;
    }; 
  }


  static class E1 extends Exception {}
  static class E2 extends E1 {}
}
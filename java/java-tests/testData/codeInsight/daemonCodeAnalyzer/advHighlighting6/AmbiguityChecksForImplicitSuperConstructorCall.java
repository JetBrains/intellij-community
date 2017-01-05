
class A {
  A(String... i) {}
  A(Integer... i) {}
}
class B extends A {
  <error descr="Ambiguous method call: both 'A.A(String...)' and 'A.A(Integer...)' match">public B()</error> {}
}
<error descr="Ambiguous method call: both 'A.A(String...)' and 'A.A(Integer...)' match">class C extends A</error> {}

class A1 {
  A1(String... i){}
  A1(Object... i){}
}
class B1 extends A1 {
  public B1() {}
}
class C1 extends A1 {}


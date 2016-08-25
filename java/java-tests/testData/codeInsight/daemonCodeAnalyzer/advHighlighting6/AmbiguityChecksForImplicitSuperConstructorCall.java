
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

class A2 {
  A2(int... i){}
  A2(Object... i){}
}
class B2 extends A2 {
  <error descr="Ambiguous method call: both 'A2.A2(int...)' and 'A2.A2(Object...)' match">public B2()</error> {
  }
}
<error descr="Ambiguous method call: both 'A2.A2(int...)' and 'A2.A2(Object...)' match">class C2 extends A2</error> {}
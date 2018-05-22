// Qualified new of static class

class A {
  b b;
  A() {
    <error descr="Qualified new of static class">b.new c()</error>;
    b.new inner();
  }
  class inner {}

  void f() {
    char[] c = <error descr="Invalid qualified new">b.new char[0]</error>;
  }
}

class b extends A {
  static class c {}
}


class WithAnonym {
  interface I {}
  class Inner{}
}

class WithAnonymInheritor extends WithAnonym {
  {
    <error descr="Anonymous class implements interface; cannot have qualifier for new">this.new I() {}</error>;
    this.new Inner() {};
  }
}
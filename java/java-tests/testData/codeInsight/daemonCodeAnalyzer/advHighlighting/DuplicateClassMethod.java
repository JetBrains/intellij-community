public class a {
  <error descr="'f(int)' is already defined in 'a'">void f(int i)</error> { }
  void f(int i) {

    new c1() {
      <error descr="'f1()' is already defined in 'Anonymous class derived from c1'">public void f1()</error> {}
      <error descr="'f1()' is already defined in 'Anonymous class derived from c1'">public void f1()</error> {}
    };
  }
}
abstract class c1 {
  abstract void f1();
}

interface ii {
  abstract void f1();
  void f2();
}

class <error descr="Duplicate class: 'a'">a</error> {
}

class DuplicateNestedClassesTest {
  public class <error descr="Duplicate class: 'C'">C</error> {}
  public class <error descr="Duplicate class: 'C'">C</error> {}
  public class <error descr="Duplicate class: 'C'">C</error> {}
}

class DuplicateLocalClassesTest {
  public void foo() {
    class D {}
    class <error descr="Duplicate class: 'D'">D</error> {}
    class <error descr="Duplicate class: 'D'">D</error> {}
  }
}

class Foo {
    void f() {
        class Bar {
        }
        class <error descr="Duplicate class: 'Bar'">Bar</error> {
        }
    }
}


class c2 {
  class c3 {
    void f() {
      class <error descr="Duplicate class: 'c2'">c2</error> {
      }
    }
  }
}


class cont {
        class <error descr="Duplicate class: 'B'">B</error> {
        }
        {
            class B {
            }
        }
        class <error descr="Duplicate class: 'B'">B</error> {
        }
}
class cont2 {
        {
            class B {
            }
        }
        class B {
        }
}
class ok {
        class Local{};
        class AnotherLocal {
            class Local {};
            void bar() {
                class Local {};
                Local l;
            }
        }
}

class ok2 {
  public ok2() {}
  public void ok2() {}
}

public class Outer {
  public class <error descr="Duplicate class: 'Outer'">Outer</error> {
    public<error descr="Identifier or type expected">;</error>
    public<error descr="Identifier or type expected">;</error>
    public<error descr="Identifier or type expected">;</error>
    public<error descr="Identifier or type expected">;</error>
  }
}
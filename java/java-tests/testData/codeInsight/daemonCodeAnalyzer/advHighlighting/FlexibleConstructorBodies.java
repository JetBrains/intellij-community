import java.util.List;

class A {
  int i;

  A() {
    i = 10;
    (this).i = 11;
    (A.this).i = 12;
    <error descr="Cannot reference 'this' before superclass constructor is called">this</error>.i++;                   // Error
    <error descr="Cannot reference 'this' before superclass constructor is called">this</error>.hashCode();            // Error
    System.out.print(<error descr="Cannot reference 'this' before superclass constructor is called">this</error>);     // Error
    Runnable r = () -> {
      <error descr="Cannot reference 'A.i' before superclass constructor is called">i</error> = 1;
      <error descr="Cannot reference 'this' before superclass constructor is called">this</error>.i = 1;
      <error descr="Cannot reference 'A.this' before superclass constructor is called">A.this</error>.i = 1;
    };
    super();
  }
  A(int i) {}
}

class B extends A {
  B() {
    int i = 3;
    super();
  }
  B(int i) {
    this();
   <error descr="Only one explicit constructor call allowed in constructor">super(2)</error>;
  }
  B(char i) {
    super(4);
   <error descr="Only one explicit constructor call allowed in constructor">this()</error>;
  }

  B(String s) {
    try {
     <error descr="Call to 'super()' must be top-level statement in constructor body">super(2)</error>;
    }
    finally {
    }
  }
  B(String s, int i) {
    {
     <error descr="Call to 'super()' must be top-level statement in constructor body">super(2)</error>;
    }
  }
  B(boolean b, int i) {
    <error descr="Cannot reference 'super' before superclass constructor is called">super</error>.i = i;
    <error descr="Cannot reference 'this' before superclass constructor is called">this</error>.i = i;
    if (false) <error descr="'return' not allowed before 'super()' call">return;</error>
    super(i);
  }

  void f() {
      <error descr="Call to 'super()' only allowed in constructor body">super()</error>;
  }
  void g() {
      <error descr="Call to 'this()' only allowed in constructor body">this()</error>;
  }

}
class D {
  int i;
}

class E extends D {

  E() {
    <error descr="Cannot reference 'super' before superclass constructor is called">super</error>.i++;                  // Error
    super();
  }

}
class F {

  int i;

  F() {
    <error descr="Cannot reference 'F.i' before superclass constructor is called">i</error>++;                        // Error
    <error descr="Cannot call 'Object.hashCode()' before superclass constructor is called">hashCode</error>();                 // Error
    super();
  }

}
class G {

  int b;

  class C {

    int c;

    C() {
      G.this.b++;             // Allowed - enclosing instance
      <error descr="Cannot reference 'C.this' before superclass constructor is called">C.this</error>.c++;             // Error - same instance
      super();
    }

  }

}
class Outer {

  void hello() {
    System.out.println("Hello");
  }

  class Inner {

    Inner() {
      hello();                // Allowed - enclosing instance method
      super();
    }

  }

}
class Outer2 {

  class Inner {
  }

  Outer2() {
    new <error descr="Cannot reference 'Inner' before superclass constructor is called">Inner</error>();                // Error - 'this' is enclosing instance
    super();
  }

}
class X {

  class S {
  }

  X() {
    var tmp = new <error descr="Cannot reference 'S' before superclass constructor is called">S</error>() { };      // Error
    super();
  }

}
class O {

  class S {
  }

  class U {

    U() {
      var tmp = new S() { };  // Allowed
      super();
    }

  }

}
class Y {
  Y(Object o) {
    if (o == null) throw new NullPointerException();
    super();
  }
}
class Z<T> extends Y {

  Z() {
    super(<error descr="Cannot reference 'this' before superclass constructor is called">this</error>);                // Error - refers to 'this'
  }

  Z(List<?> list) {
    super((T)list.get(0));      // Allowed - refers to 'T' but not 'this'
  }

}
record R(int x, int y) {
  R(int x, int y, int z) {
    if (z > 1000) throw new IllegalArgumentException();
    this(x, y);
  }
}
enum EE {
  A, B;

  EE() {
    System.out.println(1);
    this(1);
  }
  EE(int i) {}
}
class InitializeField {
  private int i = 0;
  InitializeField() {
    <error descr="Cannot assign initialized field 'InitializeField.i' before superclass constructor is called">i</error> = 1;
    <error descr="Cannot assign initialized field 'InitializeField.i' before superclass constructor is called">this.i</error> =  1;
    <error descr="Cannot assign initialized field 'InitializeField.i' before superclass constructor is called">InitializeField.this.i</error> = 1;
    super();
  }
}
class Person {

  Person(Other other) {
  }

  Person() {
    Other o = new Other(<error descr="Cannot call 'Object.hashCode()' before superclass constructor is called">hashCode</error>()) {};
    this(o);
  }

  Person(int i) {
    this(new Other(<error descr="Cannot call 'Object.hashCode()' before superclass constructor is called">hashCode</error>()));
  }
}
class Other {
  Other(int x) {}
}
class Machine {
  private final boolean big;
  Machine(boolean big) {
    this.big = big;
  }

  Machine() {
    <error descr="Cannot assign final field 'big' before chained constructor call">big</error> = false;
    this(false);
  }

  Machine(int size) {
    this(<error descr="Cannot assign final field 'big' before chained constructor call">big</error> = size > 10);
  }
}
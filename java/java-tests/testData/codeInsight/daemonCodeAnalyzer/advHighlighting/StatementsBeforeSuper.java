import java.util.List;

class A {
  int i;

  A() {
    <error descr="Cannot reference 'this' before supertype constructor has been called">this</error>.i++;                   // Error
    <error descr="Cannot reference 'this' before supertype constructor has been called">this</error>.hashCode();            // Error
    System.out.print(<error descr="Cannot reference 'this' before supertype constructor has been called">this</error>);     // Error
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
     <error descr="Call to 'super()' must be a top level statement in constructor body">super(2)</error>;
    }
    finally {
    }
  }
  B(String s, int i) {
    {
     <error descr="Call to 'super()' must be a top level statement in constructor body">super(2)</error>;
    }
  }
  B(boolean b, int i) {
    if (false) <error descr="return not allowed before 'super()' call">return;</error>
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
    <error descr="Cannot reference 'D.i' before supertype constructor has been called">super.i</error>++;                  // Error
    super();
  }

}
class F {

  int i;

  F() {
    <error descr="Cannot reference 'F.i' before supertype constructor has been called">i</error>++;                        // Error
    <error descr="Cannot reference 'Object.hashCode()' before supertype constructor has been called">hashCode</error>();                 // Error
    super();
  }

}
class G {

  int b;

  class C {

    int c;

    C() {
      G.this.b++;             // Allowed - enclosing instance
      <error descr="Cannot reference 'C.this' before supertype constructor has been called">C.this</error>.c++;             // Error - same instance
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
    new <error descr="Cannot reference 'Inner' before supertype constructor has been called">Inner</error>();                // Error - 'this' is enclosing instance
    super();
  }

}
class X {

  class S {
  }

  X() {
    var tmp = new <error descr="Cannot reference 'S' before supertype constructor has been called">S</error>() { };      // Error
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
    super(<error descr="Cannot reference 'this' before supertype constructor has been called">this</error>);                // Error - refers to 'this'
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
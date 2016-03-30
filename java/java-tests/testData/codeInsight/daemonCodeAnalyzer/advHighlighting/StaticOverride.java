// method override
import java.io.*;
import java.net.*;

class a extends a1 {
 <error descr="Static method 'f()' in 'a' cannot override instance method 'f()' in 'a1'">public static void f()</error> { }
 <error descr="Instance method 'f1()' in 'a' cannot override static method 'f1()' in 'a1'">public void f1()</error> { }
}
class a1 {
 public void f() {}
 public static void f1() {}
}

interface i {
 void f1();
}

<error descr="Static method 'f1()' in 'a1' cannot override instance method 'f1()' in 'i'">class c_a1_i extends a1 implements i</error> {
}

interface ii {
 int f();
}

<error descr="'f()' in 'a1' clashes with 'f()' in 'ii'; attempting to use incompatible return type">abstract class c_a1_ii extends a1 implements ii</error> {
}

interface i2 {
 int f1();
}
<error descr="'f1()' in 'i2' clashes with 'f1()' in 'i'; methods have unrelated return types">interface i3 extends i, i2</error> {
}

class weak {
  void f1() {}
}
<error descr="'f1()' in 'weak' clashes with 'f1()' in 'i'; attempting to assign weaker access privileges ('package-private'); was 'public'">class a2 extends weak implements i</error> {
}

class a3 {
  protected void f1() {}
}
<error descr="'f1()' in 'a3' clashes with 'f1()' in 'i'; attempting to assign weaker access privileges ('protected'); was 'public'">class a4 extends a3 implements i</error> {
//  public void f1() {}
}
class a5 extends a3 implements i {
  // if we override suspicious method, its OK
  public void f1() {}
}



// deep inherit
class da1 { void f() {} }
class da2 extends da1 { void f() {} }
class da3 extends da2 {}




interface MyInterface
{
    public void myMethod();
}
class MyInterfaceImpl implements MyInterface 
{
    <error descr="Static method 'myMethod()' in 'MyInterfaceImpl' cannot override instance method 'myMethod()' in 'MyInterface'">public static void myMethod()</error> { /* implementation goes here */ }

    <error descr="Static method 'toString()' in 'MyInterfaceImpl' cannot override instance method 'toString()' in 'java.lang.Object'">private static String toString()</error> {
        return null;
    }

}



// Sun-style inheritance
class Sunc {
  protected void f() {}
}
class Suncc extends Sunc  {
  public void f() {}
}
interface Suni {
  public void f();
}
class Sunccc extends Suncc implements Suni {
}

// override static
class StA {
  public static StA createInstance() {
    return new StA();
  }
}
class StB extends StA {
  public static <error descr="'createInstance()' in 'StB' clashes with 'createInstance()' in 'StA'; attempting to use incompatible return type">String</error> createInstance() {
    return null;
  }
}

////////
class Foo {
    protected static void foo(String s) {}
}
class Bar extends Foo{
    <error descr="'foo(String)' in 'Bar' clashes with 'foo(String)' in 'Foo'; attempting to assign weaker access privileges ('private'); was 'protected'">private</error> static void foo(String s) {}
}


/////////////  IDEADEV-41779
class A {
    public static C C() { return new C(); }
}
class B extends A {
}
class C extends B {
    public C() {}
}
///////////////////////////
class Z1 {
    public static final void doItBaby() {
        System.out.println("Hello, diar A");
    }
}

class Z2 extends Z1 {
    <error descr="'doItBaby()' cannot override 'doItBaby()' in 'Z1'; overridden method is final">public static void doItBaby()</error> {
        System.out.println("Hello, diar B");
    }
}
///////////////////
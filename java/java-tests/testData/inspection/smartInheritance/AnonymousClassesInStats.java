class MyInheritor implements A0<caret> {

}

interface A0 {

}

class A implements A0 {}

class C implements A0 {}
class D extends C {}

class Some {
  void m() {
    A a1 = new A() {};
    A a3 = new A() {};
    A a4 = new A() {};
    A a5 = new A() {};
    A a2 = new A() {};
  }
}
class Test {
  void foo(Object o) {
    <selection>
    if (true) {
      ((A1)o).doSmth();
    } else {
      ((A2)o).doSmth();
    }
    </selection>
  }
}

class A {void doSmth(){}}
class A1 extends A {}
class A2 extends A {}
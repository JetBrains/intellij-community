class Test {
  void foo(Object o) {
    <selection>
    if (true) {
      ((A1)o).doSmth();
    } else {
      o.toString();
    }
    </selection>
  }
}

class A {void doSmth(){}}
class A1 extends A {}
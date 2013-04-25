class Some {
  void m() {
    A someA = new A () {}<caret>
  }
}

class A {}

class B extends A {}

class B1 extends B {}
class B3 extends B {}
class B4 extends B {}
class B5 extends B {}
class B3 extends B {}
class B6 extends B {}


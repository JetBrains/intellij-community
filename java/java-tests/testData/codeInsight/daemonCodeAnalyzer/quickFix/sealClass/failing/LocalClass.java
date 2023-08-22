interface Par<caret>ent {
}

class Inheritor implements Parent {
}

class A {
  void foo() {
    class Local implements Parent {
    }
  }
}
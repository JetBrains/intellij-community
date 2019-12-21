
class A extends C {
  void <caret>foo() {
    A.D<String> d = new A.D<>();
  }

  static class D<T> {
  }
}

class C {

}

class Cls1 {
  static class Inner {
    Inner() {}

      static Inner createInner() {
          return new Inner();
      }
  }
}
class Cls2 {
  Cls1.Inner inner = new Cls1.Inner() {};
}
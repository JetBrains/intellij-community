class Cls1 {
  static class Inner {
    <caret>Inner() {}
  }
}
class Cls2 {
  Cls1.Inner inner = new Cls1.Inner() {};
}
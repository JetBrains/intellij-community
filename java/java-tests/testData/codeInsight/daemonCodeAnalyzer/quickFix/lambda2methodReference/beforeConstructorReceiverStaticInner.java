// "Replace lambda with method reference" "true"
class StaticInner {

  static class Inner {
    Inner(StaticInner outer) {}
  }


  interface I1 {
    Inner m(StaticInner rec);
  }


  static {
    I1 i1 = (rec) -> <caret>new Inner(rec);
  }
}
// "Replace lambda with method reference" "true-preview"
class StaticInner {

  static class Inner {
    Inner(StaticInner outer) {}
  }


  interface I1 {
    Inner m(StaticInner rec);
  }


  static {
    I1 i1 = Inner::new;
  }
}
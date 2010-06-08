public class A extends AA {
  <caret>
  static class C extends D {}
  static class D extends B {}
  static class B {}
}

class AA {}
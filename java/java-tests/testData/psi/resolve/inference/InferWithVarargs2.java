class C1 {}
class C2 extends C1 {}

class C {
  <T> T foo (T... ts) { return null; }

  void bar () {
    <ref>foo(new C2(), new C1());
  }
}
// "Create local variable 'a'" "true-preview"
class C {
  void foo(Foo f) {
      <caret>a = f;
      if (a != null && a.foo()) {}
   }
}
class Foo {}
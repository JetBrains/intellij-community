// "Create local variable 'a'" "true"
class C {
  void foo(Foo f) {
      <caret>a = f;
      if (a != null && a.foo()) {}
   }
}
class Foo {}
// "Create local variable 'a'" "true-preview"
class C {
  void foo(Foo f) {
      Foo a = f;
      if (a != null && a.foo()) {}
   }
}
class Foo {}
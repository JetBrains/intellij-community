// "Create local variable 'a'" "true"
class C {
  void foo(Foo f) {
      Foo a = f;
      if (a != null && a.foo()) {}
   }
}
class Foo {}
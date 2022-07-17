public class Foo {
  void bar() {
    foo("a.b.c")
  }
  
  void foo(@org.jetbrains.annotations.PropertyKey(resourceBundle = "Bundle") String key) { }
}

class Bundle {}
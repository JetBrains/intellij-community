public class Foo {
  void b<caret>ar() {
    foo("a.b.c")
  }
  
  void foo(@org.jetbrains.annotations.PropertyKey(resourceBundle = "Bundle") String key) { }
}

class Bundle {}
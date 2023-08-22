
class Main {
  int f(Sealed o) {
    return switch(o) {
      <caret>
    }
  }

  void f(Sealed o) {
    switch(o) {
      <caret>
    }
  }
}

sealed interface Sealed permits Variant1, Variant2 { }
final class Variant1 implements Sealed { }
final class Variant2 implements Sealed { }
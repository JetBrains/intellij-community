
class Main {
  void f(Sealed o) {
    switch(o) {
      <caret>
    }
  }

  private static sealed interface Sealed permits Variant1, Variant2 { }
  private static final class Variant1 implements Sealed { }
  private static final class Variant2 implements Sealed { }
}

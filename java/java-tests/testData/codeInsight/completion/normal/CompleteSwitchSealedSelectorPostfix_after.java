
class Main {
  int h(Sealed o) {
      switch (o) {
          <caret>
      }
  }

  public static sealed interface Sealed {}
  public static final class Variant {}
}
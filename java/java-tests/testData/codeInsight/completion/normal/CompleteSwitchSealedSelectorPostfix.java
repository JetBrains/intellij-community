
class Main {
  int h(Sealed o) {
    o.swit<caret>
  }

  public static sealed interface Sealed {}
  public static final class Variant {}
}
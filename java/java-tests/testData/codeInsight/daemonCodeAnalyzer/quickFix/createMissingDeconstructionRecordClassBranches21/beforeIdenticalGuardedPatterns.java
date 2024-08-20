// "Create missing branch 'Pair<I>(I x, D y)'" "true-preview"
sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}
record Pair<T>(T x, T y) {}

class Test {
  void test(Pair<I> i) {
    switch (i<caret>) {
      case Pair<I>(I f, I s) when Math.random() > 0.5 -> {}
      case Pair<I>(I f, I s) when Math.random() > 0.1 -> {}
      case Pair<I>(I f, I s) when Math.random() > 0.5 -> {}
      case Pair<I>(I f, C s) -> {}
    }
  }
}
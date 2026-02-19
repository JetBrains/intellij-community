// "Create missing branch 'Pair<I>(C y, int x)'" "true-preview"
sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}
record Pair<T>(T y, int x) {}

class Test {
  void foo3(Pair<I> pair) {
    switch (pair) {
        case Pair<I>(D a, int f) ->{}
        case Pair<I>(C y, int x) -> {
        }
    }
  }
}
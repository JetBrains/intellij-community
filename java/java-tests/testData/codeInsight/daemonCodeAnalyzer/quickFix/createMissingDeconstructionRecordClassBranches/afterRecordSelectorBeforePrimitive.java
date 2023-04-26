// "Create missing switch branch 'Pair<I>(int x, C y)'" "true-preview"
sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}
record Pair<T>(int x, T y) {}

class Test {
  void foo3(Pair<I> pair) {
    switch (pair) {
        case Pair<I>(int f, D a) ->{}
        case Pair<I>(int x, C y) -> {
        }
    }
  }
}
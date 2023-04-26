// "Create missing branches: 'Pair<I>(D x, C y)', and 'Pair<I>(C x, C y)'" "true-preview"
sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}
record Pair<T>(T x, T y) {}

class Test {
  void foo3(Pair<I> pair) {
    switch (pair<caret>) {
        case Pair<I>(D f, D a) ->{}
        case Pair<I>(C f, D a) ->{}
        case null -> {}
    }
  }
}
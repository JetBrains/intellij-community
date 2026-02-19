// "Create missing branch 'Pair<Boxed,Boxed>(Box b1, Boxed b2)'" "true-preview"
record Pair<T, K>(T b1, K b2) {
}

sealed interface Boxed permits Box, Box2 {
}

record Box(L1 value) implements Boxed {
}

record Box2(L1 value) implements Boxed {
}

sealed interface L1 {
}

final class L21 implements L1 {
}

final class L22 implements L1 {
}

class Test {
  void foo5(Pair<Boxed, Boxed> o) {
    switch (o) {
      case Pair<Boxed, Boxed>(Box(Object v1), Box(Object v52)) -> {
      }
      case Pair<Boxed, Boxed>(Box2 b1, Boxed b2) -> {
      }
        case Pair<Boxed, Boxed>(Box b1, Boxed b2) -> {
        }
    }
  }
}
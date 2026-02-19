// "Create missing branch 'Pair<Box,Box>(Box b1, Box b2)'" "true-preview"

record Rec(L1 l1) {}

record Pair<T, K>(T b1, K b2) {}

sealed interface Boxed permits Box, Box2 {}

record Box(L1 value) implements Boxed {}

record Box2(L1 value) implements Boxed {}

sealed interface L1 {}

final class L21 implements L1 {}

final class L22 implements L1 {}
class Test {
  void foo(Pair<Box, Box> o) {
    switch (o<caret> ) {
      case Pair<Box, Box>(Box(L21 v1), Box(L22 v2)) -> {
      }
    }
  }
}
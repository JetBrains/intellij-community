// "Create missing switch branch 'PairString<Boxed>(Box2 t, String t2)'" "true-preview"

record Rec(L1 l1) {}

record Pair<T, K>(T b1, K b2) {}

sealed interface Boxed permits Box, Box2 {}

record Box(L1 value) implements Boxed {}

record Box2(L1 value) implements Boxed {}

sealed interface L1 {}

final class L21 implements L1 {}

final class L22 implements L1 {}

record PairString<T>(T t, String t2) { }

record StringPair<T>(String t2, T t) { }

class Test {
  void foo4(PairString<Boxed> o) {
    switch (o) {
      case PairString<Boxed>(Box(L21 v1), String t) -> {
      }
      case PairString<Boxed>(Box(L22 v2), String t2) -> {
      }
        case PairString<Boxed>(Box2 t, String t2) -> {
        }
    }
  }
}
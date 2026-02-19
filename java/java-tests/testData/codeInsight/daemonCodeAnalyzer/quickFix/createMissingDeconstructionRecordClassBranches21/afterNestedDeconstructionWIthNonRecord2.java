// "Create missing branch 'StringPair<Boxed>(String t2, Box t)'" "true-preview"

sealed interface Boxed permits Box, Box2 {}

record Box(L1 value) implements Boxed {}

record Box2(L1 value) implements Boxed {}

sealed interface L1 {}

final class L21 implements L1 {}

final class L22 implements L1 {}

record StringPair<T>(String t2, T t) { }

class Test {

  void foo5(StringPair<Boxed> o) {
    switch (o) {
      case StringPair<Boxed>(String t, Box(L21 v1)) -> {
      }
      case StringPair<Boxed>(String t2, Box2 t) -> {
      }
        case StringPair<Boxed>(String t2, Box t) -> {
        }
    }
  }
}
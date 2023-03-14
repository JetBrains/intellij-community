public class SwitchExhaustivenessIn20Java {
  class A {}
  class B extends A {}
  sealed interface I<T> permits C, D {}
  record C<T>(T t) implements I<T> {}
  final class D implements I<String> {}
  record Pair<T>(T x, T y){
  }
  void foo(Pair<I<? extends String>> pairI) {
    switch (<error descr="'switch' statement does not cover all possible input values">pairI</error>) {
      case Pair<I<? extends String>>(C<? extends CharSequence>(String i), D snd) -> {}
      case Pair<I<? extends String>>(I<? extends CharSequence> fst, C snd) -> {}
      case Pair<I<? extends String>>(D fst, I snd) -> {}
    }
  }

  void foo2(Pair<I<? extends String>> pairI) {
    switch (pairI) {
      case Pair<I<? extends String>>(C<? extends CharSequence>(CharSequence i), D snd) -> {}
      case Pair<I<? extends String>>(I<? extends CharSequence> fst, C snd) -> {}
      case Pair<I<? extends String>>(D fst, I snd) -> {}
    }
  }

  sealed interface L1 {
  }

  final class L21 implements L1 {
  }
  final class L22 implements L1 {
  }

  sealed interface Boxed permits Box, Box2 {}

  record Box(L1 value) implements Boxed {}

  record Box2(L1 value) implements Boxed {}

  void foo3(Pair<Box> o) {
    switch (o) {
      case Pair(Box(L21 v1), Box(L22 v2)) -> {
      }
      case Pair(Box b1, Box b2) -> {
      }
    }
  }
  void foo4(Pair<Boxed> o) {
    switch (o) {
      case Pair<Boxed>(Box(L21 v1), Box2(L22 v2)) -> {
      }
      case Pair<Boxed>(Box(Object v1), Box(Object v2)) -> {
      }
      case Pair<Boxed>(Box2 b1, Boxed b2) -> {
      }
      case Pair<Boxed>(Box b1, Box2 b2) -> {
      }
    }
  }

  record PairString<T>(T t, String t2){}

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

  record StringPair<T>(String t2, T t) {

  }

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
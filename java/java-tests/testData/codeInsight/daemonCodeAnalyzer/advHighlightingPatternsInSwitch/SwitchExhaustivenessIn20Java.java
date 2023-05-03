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
}
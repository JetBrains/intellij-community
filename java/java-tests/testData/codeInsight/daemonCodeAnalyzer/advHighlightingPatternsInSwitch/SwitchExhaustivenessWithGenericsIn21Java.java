sealed interface I<X> permits A, B, C, D, E, F, J {}
final class A<Y> implements I<String> {}
final class B implements I<String> {}
final class C<T> implements I<T> {}
final class D<T> implements I<Integer> {}
final class E implements I<Integer> {}
final class F<T1, T2, T3> implements I<T3> {}
final class J<T1, T2, T3> implements I<String> {}

record R<T>(I<T> i) {}

class Main {

  static void testRecordExhaustive(R<Integer> r){
    switch (r) {
      case R<Integer>(C<Integer> i) -> {}
      case R<Integer>(D<?> d) -> {}
      case R<Integer>(E e) -> {}
      case R<Integer>(F<?, ?, Integer> f) -> {}
    }
  }

  static void testRecordNotExhaustive(R<Integer> r){
    switch (<error descr="'switch' statement does not cover all possible input values">r</error>) {
      case R<Integer>(D<?> d) -> {}
      case R<Integer>(E e) -> {}
      case R<Integer>(F<?, ?, Integer> f) -> {}
    }
  }

  static int testExhaustive1(I<Integer> i) {
    return switch(i) {
      case C<Integer> c -> 42;
      case D<?> d -> 43;
      case E e -> 43;
      case F<?, ?, Integer> f -> 42;
    };
  }

  static int testExhaustive2(I<Integer> i) {
    return switch(i) {
      case C<Integer> c -> 42;
      case D d -> 43;
      case E e -> 43;
      case F<?, ?, Integer> f -> 42;
    };
  }

  static int testNotExhaustive1(I<Integer> i) {
    return switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case D<?> d -> 43;
      case E e -> 43;
      case F<?, ?, Integer> f -> 42;
    };
  }

  static int testNotExhaustive2(I<Integer> i) {
    return switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case C<Integer> c -> 42;
      case E e -> 43;
      case F<?, ?, Integer> f -> 42;
    };
  }

  static int testNotExhaustive3(I<Integer> i) {
    return switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case C<Integer> c -> 42;
      case D<?> d -> 43;
      case F<?, ?, Integer> f -> 42;
    };
  }

  static int testNotExhaustive4(I<Integer> i) {
    return switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case C<Integer> c -> 42;
      case D<?> d -> 43;
      case E e -> 43;
    };
  }
}
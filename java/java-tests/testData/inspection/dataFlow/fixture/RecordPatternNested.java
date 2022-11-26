class Test {
  void foo(Object obj) {
    if (!(obj instanceof Bar)) {
      return;
    }

    if (obj instanceof Bar(I2 s) bar) { //can be false?
      System.out.println(bar);
    }
  }
}

record Bar(I s) {
}

sealed interface I {}
final class I1 implements I {}
final class I2 implements I {}


class Test {
  void foo(Object obj) {
    if (!(obj instanceof Bar)) {
      return;
    }

    if (obj instanceof Bar(I2 s)) { //can be false?
      System.out.println(s);
    }
  }
}

record Bar(I s) {
}

sealed interface I {}
final class I1 implements I {}
final class I2 implements I {}


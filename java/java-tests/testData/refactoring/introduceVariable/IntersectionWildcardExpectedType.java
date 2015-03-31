import java.util.Arrays;

class Test {

  interface I {}
  enum X implements I  {a}
  enum Y implements I {a}

  {
    <selection>Arrays.asList(X.a, Y.a)</selection>;
  }
}

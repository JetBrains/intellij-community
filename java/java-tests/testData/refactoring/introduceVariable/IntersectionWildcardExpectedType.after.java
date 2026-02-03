import java.util.Arrays;
import java.util.List;

class Test {

  interface I {}
  enum X implements I  {a}
  enum Y implements I {a}

  {
      List<? extends Enum<? extends Enum<?>>> l = Arrays.asList(X.a, Y.a);
  }
}

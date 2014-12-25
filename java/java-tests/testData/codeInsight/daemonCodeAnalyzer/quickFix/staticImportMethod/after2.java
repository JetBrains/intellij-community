// "Static import method..." "true"
package p;

import static p.FFF.myEqualTo;

public class X {
  public void test() throws Exception {
    assertMe("", myEqualTo(""));
  }

  <V> void assertMe(V v, M<V> m) {
  }
}


class M<T> {
}

class FFF {
  public static <T> M<T> myEqualTo(T operand) {
    return null;
  }
}


class LLL {
  public static M<String> myEqualTo(String string) {
    return null;
  }
}
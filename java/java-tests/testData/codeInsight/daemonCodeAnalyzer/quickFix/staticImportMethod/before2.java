// "Static import method..." "true"
package p;
public class X {
  public void test() throws Exception {
    assertMe("", my<caret>EqualTo(""));
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
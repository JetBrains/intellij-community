import org.jetbrains.annotations.Contract;

class X {
  @Contract("!null -> fail")
  static native void assertNull(Object x);

  @Contract("null -> null")
  static native String toString(Object obj);

  void test(Object x, Object y) {
    assertNull(x);
    if(toString(y).isEmpty()) {

    }
  }
}
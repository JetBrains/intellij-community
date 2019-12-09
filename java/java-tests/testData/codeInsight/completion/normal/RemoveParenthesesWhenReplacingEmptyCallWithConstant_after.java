public class Util {
  int goo(C c) {
    return c.VALUE<caret>;
  }
}

class C {
  static final int VALUE = 2;
  static int getValue() {}
}
public class Util {
  int goo(C c) {
    return c.VA<caret>getValue();
  }
}

class C {
  static final int VALUE = 2;
  static int getValue() {}
}
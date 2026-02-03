package p;

import static p.C.mock;
class C {
  public static String mock() {
    return null;
  }
}

class D {
  public static final String CONST = mock();
}
class QTest {
  public static void main(String[] args) {
    String s = D.CON<caret>ST;
  }
}
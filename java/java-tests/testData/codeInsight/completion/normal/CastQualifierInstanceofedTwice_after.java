interface Common {
  String moo();
}

interface I1 extends Common {
  String foo();
}

interface I2 extends Common {
  String boo();
}

public class Main {
  public static void test(Common param) {
    if (param instanceof I1) {
      if (param instanceof I2) {
        ((I1) param).foo()<caret>
      }
    }
  }
}
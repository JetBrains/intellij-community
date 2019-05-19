public class Test {

  static final double DOUBLE_FIELD = 1.0 + 0.1;

  static void someMethod(double val) {

  }

  public static void main(String[] args) {
    someMethod(DOUBLE_FIELD);
    someMethod(Test.DOUBLE_FIELD);
  }
}
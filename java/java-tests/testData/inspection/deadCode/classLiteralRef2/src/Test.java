import java.util.Arrays;

class Test {
  public static void main(String[] args) {
    System.out.println(Arrays.toString(XXX.class.getEnumConstants()));
    System.out.println(Arrays.toString(YYY.class.getDeclaredMethods()));
  }

  enum XXX {
    Foo, Bar
  }

  // Don't think it's should be marked as used
  class YYY {
    void depeche() {}
    void mode() {}
  }
}
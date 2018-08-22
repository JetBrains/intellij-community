import java.util.List;

class Test {
  void test(String type) {
    boolean uint = false;
    if ("int".equals(type) || (uint = "uint".equals(type))) {
      System.out.println("possible");
    }
  }
}
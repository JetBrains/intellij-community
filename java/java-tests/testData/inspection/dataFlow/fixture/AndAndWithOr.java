import java.util.List;

class Test {
  void test(String type) {
    if(type != null && (type.equals("foo") | type.equals("bar"))) {
      System.out.println("Who knows");
    }
  }
}
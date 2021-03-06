import java.util.Arrays;

// "Collapse into loop" "true"
class X {
  void test() {
      for (String s : Arrays.asList("Hello", "World")) {
          System.out.println(s);
      }
  }
}
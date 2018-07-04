// "Unroll loop" "true"
import java.util.Arrays;

class Test {
  void test() {
    fo<caret>r(String s : Arrays.asList("foo", "bar")) System.out.println(s);
  }

  void foo(boolean b) {}
}

import java.util.StringJoiner;

class Test {
  void test2(String s, CharSequence s1) {
    StringJoiner s<caret>j = new StringJoiner("");
    sj.add("foo"); // comment1
    sj.add(s); // comment2
    sj.add("bar"); // comment3
    sj.add(s1); // comment4
    System.out.println(sj.toString());
  }
}
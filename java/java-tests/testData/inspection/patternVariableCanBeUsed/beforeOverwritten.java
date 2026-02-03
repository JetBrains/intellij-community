// "Replace 'i' with pattern variable" "false"
public class Example {
  static void example(Object o) {
    if (o instanceof Integer) {
      o = ((Integer) o) + 1;

      var <caret>i = (Integer) o;
      System.out.println(i);
    }
  }
}
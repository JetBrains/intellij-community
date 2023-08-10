// "Set variable type to 'int[]'" "true-preview"
public class Demo {
  void test() {
    var<caret> ints[];
    ints = new int[]{1, 2, 42};
  }
}

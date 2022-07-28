// "Replace with forEach" "true-preview"

public class Test {
  void foo(int i) {}

  void test() {
    f<caret>or(int i=0; i<10; i++) {
      System.out.println(foo(i));
    }
  }
}

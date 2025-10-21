// "Fix the typo 'ne' to 'new'" "true-preview"
public class Main {

  static void call(A a) {

  }

  static class A{}
  static void test2() {
    call(ne<caret> A());
  }
}


public class SomeClass {
  int test(char ch) {
    i<caret>f (ch == '=') {
      System.out.println();
      return 1; // hello
    }
    return 2;
  }
}
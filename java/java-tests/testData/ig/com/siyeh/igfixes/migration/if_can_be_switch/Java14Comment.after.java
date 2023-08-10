public class SomeClass {
  int test(char ch) {
      <caret>switch (ch) {
          case '=' -> {
              System.out.println();
              return 1; // hello
          }
      }
    return 2;
  }
}
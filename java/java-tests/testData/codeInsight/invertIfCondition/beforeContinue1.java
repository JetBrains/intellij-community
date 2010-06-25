// "Invert If Condition" "true"
class A {
    void foo () {
      int a = 0, b = 0;
      for (;;) {
          <caret>if (a == b) continue;
          a = b;
      }
    }
}
// "Remove redundant 'else'" "true-preview"
class a {
  void foo() {
    int a = 0;
    int b = 0;
    if (a != b) {
      return;
    } e<caret>lse {
      a = b;
    }
    a++;
  }
}


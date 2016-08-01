// "Remove redundant 'else'" "true"
class a {
  void foo() {
    int a = 0;
    int b = 0;
    if (a != b) {
      return;
    }
      a = b;
      a++;
  }
}


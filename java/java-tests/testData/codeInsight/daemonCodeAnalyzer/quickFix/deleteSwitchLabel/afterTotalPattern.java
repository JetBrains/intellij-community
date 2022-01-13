// "Remove switch branch '((Integer ii && true))'" "true"
class Test {
  Integer i = 1;
  void test() {
    switch (i) {
      case Object o:
        break;
    }
  }
}
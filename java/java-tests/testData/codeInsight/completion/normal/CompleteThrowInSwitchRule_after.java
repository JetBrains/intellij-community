
class Main {
  void test(int x) {
    switch(x) {
      case 1 -> throw <caret>
    }
  }
}
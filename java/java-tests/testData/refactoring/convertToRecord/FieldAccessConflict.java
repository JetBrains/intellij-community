class <caret>R {
  final int first;

  R(int first) {
    this.first = first;
  }

  private int getFirst() {
    return first > 0 ? first : -first;
  }
}

class Main {
  void test() {
    new R(1).first;
  }
}
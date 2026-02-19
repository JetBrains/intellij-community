// "Remove 'new'" "true"

class A<T> {
  int x() {
    return new A<Integer>.<caret>y();
  }

  static int y() {
    return 1;
  }
}

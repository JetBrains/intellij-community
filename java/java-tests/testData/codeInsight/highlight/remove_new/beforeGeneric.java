// "Remove 'new'" "false"

class A<T> {
  int x() {
    return new A<Integer>.<caret>y();
  }

  int y() {
    return 1;
  }
}

// "Remove 'new'" "true"

class A<T> {
  int x() {
    return A.y();
  }

  static int y() {
    return 1;
  }
}

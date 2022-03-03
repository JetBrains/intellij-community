// "Remove 'new'" "false"

class A {
  int x() {
    return new A.<caret>y();
  }

  public static int y = 1;
}

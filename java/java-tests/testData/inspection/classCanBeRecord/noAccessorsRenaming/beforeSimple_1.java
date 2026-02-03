// "Convert to record class" "true-preview"
class R<caret> {
  final int x;
  final int y;

  R(int x, int y) {
    this.x = x;
    this.y = y;
  }

  int getX() {
    return x;
  }

  int getY() {
    return y;
  }
}

class Foo {
  void foo() {
    R r = new R(10, 20);
    System.out.println("x: " + person.getX() + ", y" + r.getY());
  }
}

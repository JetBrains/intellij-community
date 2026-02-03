// "Convert to record class" "true-preview"
record R(int x, int y) {

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

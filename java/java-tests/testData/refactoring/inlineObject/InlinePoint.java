class Main {
  void test() {
    System.out.println(new <caret>Point(1, 2).getX());
  }
}

class Point {
  private int x, y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }
}

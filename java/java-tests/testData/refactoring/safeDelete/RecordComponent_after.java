/**
 * 
 * @param x
 * @param y
 */
record Point(int x, int y) {
  Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

    public static void main(String[] args) {
    Point point = new Point(0, 0);
  }
}
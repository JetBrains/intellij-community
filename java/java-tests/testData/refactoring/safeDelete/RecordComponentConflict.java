/**
 * 
 * @param x
 * @param y
 * @param z  remove me
 */
record Point(int x, int y, int <caret>z) {
  Point(int x, int y) {
    this(x, y, 0);
  }
  
  @Override
  public int z() {
    return z;
  }

  public static void main(String[] args) {
    Point point = new Point(0, 0);
    System.out.println(point.z());
  }
}
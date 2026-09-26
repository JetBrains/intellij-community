/**
 * 
 * @param x
 * @param y
 * @param z  remove me
 */
record Point(int x, int y, int <caret>z) {
  
  Point {
    if (x < 0 || y < 0 || z < 0) throw new IllegalArgumentException();
  }
  
  @Override
  public int z() {
    return z;
  }

  public static void main(String[] args) {
    Point point = new Point(0, 0, 0);
  }
}
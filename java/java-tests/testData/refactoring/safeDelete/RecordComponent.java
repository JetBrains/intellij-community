/**
 * 
 * @param x
 * @param y
 * @param z  remove me
 */
record Point(int x, int y, int <caret>z) {
  Point(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
  
  @Override
  public int z() {
    return z;
  }

  public static void main(String[] args) {
    Point point = new Point(0, 0, 0);
  }
}
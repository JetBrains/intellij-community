/**
 * Record javadoc
 *
 * @param y y
 * @param z z
 * @param x x
 */
record Rec(int y, int z, int x) {
  /**
   * Constructor javadoc
   *
   * @param y y
   * @param z z
   * @param x x
   */
  public Rec(int y, int z, int x) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
  
  public int x() {return x;}
  public int y() {return y;}
  public int z() {return z;}
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec(2, 3, 1);
        System.out.println(rec.x());
        System.out.println(rec.y());
        System.out.println(rec.z());
    }
}
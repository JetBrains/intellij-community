/**
 * @param y y
 * @param z z
 * @param x x
 */
record Rec(int y, int z, int x) {
  public Rec(int y, int z, int x) {
    this.y = y;
    this.z = z;
    this.x = x;
  }
  
  public int y() {return y;}
  public int z() {return z;}
  public int x() {return x;}
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec(1, 2, 3);
        System.out.println(rec.y());
        System.out.println(rec.z());
        System.out.println(rec.x());
    }
}
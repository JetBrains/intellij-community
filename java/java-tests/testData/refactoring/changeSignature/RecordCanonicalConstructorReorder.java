/**
 * Record javadoc 
 * @param x x
 * @param y y
 * @param z z
 */
record Rec(int x, int y, int z) {
  /**
   * Constructor javadoc 
   * @param x x
   * @param y y
   * @param z z
   */
  public R<caret>ec(int x, int y, int z) {
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
        Rec rec = new Rec(1, 2, 3);
        System.out.println(rec.x());
        System.out.println(rec.y());
        System.out.println(rec.z());
    }

    void foo(Object obj) {
        switch (obj) {
            case Rec(int x, int y, int z) when x + y + z == 42 -> System.out.println(x + y + z);
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        boolean b = obj instanceof Rec(int x, int y, int z) && x + y + z == 42;
    }
}
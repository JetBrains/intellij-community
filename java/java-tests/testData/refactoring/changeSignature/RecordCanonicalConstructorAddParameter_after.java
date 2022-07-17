/**
 * Record javadoc
 *
 * @param x x
 * @param y
 */
record Rec(int x, int y) {
  public Rec(int x, int y) {
    this.x = x;
  }
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec(1, );
        System.out.println(rec.x());
    }
}
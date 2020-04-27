/**
 * @param x x
 * @param y y
 * @param z z
 */
record R<caret>ec(int x, int y, int z) {
  public int y() {
      return this.y;
  }   
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec(1, 2, 3);
        System.out.println(rec.y());
    }
}
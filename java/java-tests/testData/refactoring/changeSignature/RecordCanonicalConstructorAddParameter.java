/**
 * Record javadoc 
 * @param x x
 */
record Rec(int x) {
  public R<caret>ec(int x) {
    this.x = x;
  }
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec(1);
        System.out.println(rec.x());
    }
}
/**
 * @param yyy y
 */
record Rec(long yyy) {
  public long yyy() {
      return this.yyy;
  }   
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec(2);
        System.out.println(rec.yyy());
    }
}
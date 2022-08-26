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

    void foo(Object obj) {
        switch (obj) {
            case Rec(long yyy) when yyy == 42 -> System.out.println(yyy);
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        if (!(obj instanceof Rec(long yyy) && yyy == 42)) {
            System.out.println("hello");
        }
        else {
            System.out.println(yyy);
        }
    }
}
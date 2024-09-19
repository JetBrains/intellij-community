
/// @param x x
/// @param y y
/// @param z z

record Rec(int x, int y, int z) {
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

        if (obj instanceof Rec(int x, int y, int z) rec) {
            System.out.println(x + y + z);
        }
    }

    void bar(Rec[] recs) {
      for (Rec(int x, int y, int z) : recs) {
        System.out.println(x + y + z);
      }
    }
}
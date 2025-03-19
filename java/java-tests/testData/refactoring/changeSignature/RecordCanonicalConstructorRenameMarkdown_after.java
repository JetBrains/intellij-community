
/// @param y y
/// @param z z
/// @param x x

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

    void foo(Object obj) {
        switch (obj) {
            case Rec(int y, int z, int x) when y + z + x == 42 -> System.out.println(y + z + x);
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        if (obj instanceof Rec(int y, int z, int x) rec) {
            System.out.println(y + z + x);
        }
    }

    void bar(Rec[] recs) {
      for (Rec(int y, int z, int x) : recs) {
        System.out.println(y + z + x);
      }
    }
}
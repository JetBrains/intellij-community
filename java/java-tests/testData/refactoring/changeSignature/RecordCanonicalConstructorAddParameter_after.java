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

    void foo(Object obj) {
        switch (obj) {
            case Rec(int x, int y) when x == 42 -> System.out.println(x);
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        if (obj instanceof Rec(int x, int y) && x == 42) {
            System.out.println(x);
        }
    }
}
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

    void foo(Object obj) {
        switch (obj) {
            case Rec(int x, int y, int z) when y == 42 -> System.out.println(y);
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        if (!(obj instanceof Rec(int x, int y, int z) && y == 42)) {
            System.out.println("hello");
        }
        else {
            System.out.println(y);
        }
    }
}

/// Record javadoc 
/// @param x x

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

    void foo(Object obj) {
        switch (obj) {
            case Rec(int x) when x == 42 -> System.out.println(x);
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        if (obj instanceof Rec(int x) && x == 42) {
            System.out.println(x);
        }
    }
}
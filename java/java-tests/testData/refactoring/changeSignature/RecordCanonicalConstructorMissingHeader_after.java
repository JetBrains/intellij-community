record Rec(int x) {
  public Rec(int x) {
  }
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec();
    }

    void foo(Object obj) {
        switch (obj) {
            case Rec(int x) -> System.out.println();
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        boolean b = !(obj instanceof Rec(int x) rec) ? false : true;
    }
}
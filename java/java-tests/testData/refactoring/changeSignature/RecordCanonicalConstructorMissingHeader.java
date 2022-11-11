record Rec {
  public R<caret>ec() {
  }
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec();
    }

    void foo(Object obj) {
        switch (obj) {
            case Rec() -> System.out.println();
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }

        boolean b = !(obj instanceof Rec() rec) ? false : true;
    }
}
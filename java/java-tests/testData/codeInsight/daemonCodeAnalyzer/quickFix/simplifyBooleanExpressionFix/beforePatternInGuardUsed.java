// "Simplify 'u instanceof Upper(Lower l)' to true (may change semantics)" "false"
class X {
  void test() {
    record Lower(int a, int b) {
    }
    record Upper(Lower lower) {
    }

    Object rec = new Upper(new Lower(0, 0));
    switch (rec) {
      case Upper u when u instanceof <caret>Upper(Lower l) && l instanceof Lower(int a, int b) -> {
        System.out.println(u);
        System.out.println(l);
        System.out.println(a);
      }
      default -> throw new IllegalStateException("Unexpected value: " + rec);
    }
  }
}
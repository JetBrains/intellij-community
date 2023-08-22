enum E {
  A(new Bar().foo(), 1);

  E(String s, int num) {
    this.s = s;
    this.num = num;
  }

  private final String s;
  private final int num;
}

class Bar {
  String foo() {
    return "hello";
  }
}
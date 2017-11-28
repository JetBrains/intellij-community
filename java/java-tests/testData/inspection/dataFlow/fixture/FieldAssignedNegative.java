class FieldReassignInt {
  void test() {
    Holder res = new Holder();
    int pos = getSomething();
    if (pos == -1) System.out.println("oops");
    res.position = pos;
    if (pos == -1) System.out.println("oops");
    if (res.position == -1) System.out.println("oops");
    System.out.println(res);
  }

  static class Holder {
    int position;
  }

  int getSomething() {
    return 42;
  }
}
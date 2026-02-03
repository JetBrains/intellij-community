class Pair<A, B> {
  public final A first;
  public final B second;

  Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }
}

class Test {
    void test(Pair<String, Integer> p) {
        Pair<String, Integer> p2 = new Pair<String, Integer>(p.first, <caret>);
    }
}
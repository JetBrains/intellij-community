import java.util.*;

class Test {
  static class Pair<A, B> {
    Pair(A a, B b) {
    }

    static <A, B> Pair<A, B> create(A a, B b) {
      return new Pair<A, B>(a, b);
    }
  }

  public static void getWordsWithOffset(String s, int startInd, final List<Pair<String, Integer>> res) {
    res.add(Pair.create(s, startInd));
  }
}

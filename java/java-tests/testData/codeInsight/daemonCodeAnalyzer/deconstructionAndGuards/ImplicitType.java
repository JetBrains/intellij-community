record Sample(String x, int y){}
record Pair<T>(T x, T y) {
}


class Test {
  void test1(Object o){
    if (o instanceof Sample(var a, var b)) {

    }
  }

  void test2(Pair<Integer> pair) {
    switch (pair) {
      case Pair<Integer>(var fst, var snd) -> {
        Integer num = fst;
      }
    }
  }
}

import java.util.function.Function;

class Test {
  void test() {
    int val<caret>ue1 = 10;

    Function<Integer, Integer> f = x -> x + value1;
  }
}
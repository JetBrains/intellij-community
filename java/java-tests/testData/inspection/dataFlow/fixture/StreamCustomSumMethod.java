import java.util.stream.Stream;

class Test {
  interface MyStream extends Stream<String> {
    String sum();
  }

  void test(MyStream stream) {
    String s = stream.sum();
  }
}

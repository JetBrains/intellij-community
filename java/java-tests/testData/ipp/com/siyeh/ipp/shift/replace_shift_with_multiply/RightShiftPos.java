import java.util.List;

class Test {
  void test(List<String> foo) {
      int x = foo.size() >> 1<caret>2;
  }
}

// "Fix all 'Unused declaration' problems in file" "true"
public class Test {
  record R(int x, int y) {}
  void test(Object obj) {
    if (obj instanceof R(_, _)) {

    }
  }
}
import java.util.*;

abstract class A implements Iterable<String> {}

class Test {
  void test(A it) {
    for(Integer s : it) {
    }
  }
}

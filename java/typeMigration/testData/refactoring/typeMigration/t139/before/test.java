import java.util.*;

abstract class A implements Iterable<String> {}

class Test {
  void test(A it) {
    for(String s : it) {
    }
  }
}

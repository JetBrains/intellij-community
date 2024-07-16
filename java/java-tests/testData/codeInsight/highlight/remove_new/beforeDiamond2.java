// "Remove 'new'" "true"
import java.util.*;

class A {
  void test() {
    new Arrays.<caret>asList<>();
  }
}

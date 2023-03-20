// "Replace 'E2.class' with 'E1.class'" "true-preview"
import java.util.EnumSet;
import java.util.Set;

public class Demo {
  void test2() {
    Set<E1> set = EnumSet.<caret>allOf(E2.class);
  }

  enum E1 {}
  enum E2 {}
}
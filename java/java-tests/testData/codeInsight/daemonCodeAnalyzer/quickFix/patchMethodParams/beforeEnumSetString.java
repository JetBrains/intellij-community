// "Replace 'E2.class' with 'String.class'" "false"
import java.util.EnumSet;
import java.util.Set;

public class Demo {
  void test2() {
    Set<String> set2 = EnumSet.<caret>allOf(E2.class);
  }

  enum E2 {}
}
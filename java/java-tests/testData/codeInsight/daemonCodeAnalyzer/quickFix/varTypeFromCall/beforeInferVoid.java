// "Change variable 'l' type to 'List<Void>'" "false"
import java.util.List;

public class Test {
  void foo() {
    List<String> l = null;
    l.add(b<caret>ar());
  }
  void bar() {
  }
}

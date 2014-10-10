// "Make 'bar' return 'java.util.Iterator'" "true"
import java.util.ArrayList;
public class Foo {
  void bar() {
    return new ArrayList().itera<caret>tor();
  }
}

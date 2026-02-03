// "Make 'method()' return 'java.util.List<T>'" "false"
import java.util.*;

public class Test {
  static class Inner<T> {
    static void method() {}

    void test() {
      List<T> t = met<caret>hod();
    }
  }
}

// "Make 'method()' return 'java.util.List<T>'" "false"
import java.util.*;

public class Test {
  void method() {}

  class Inner<T> {

    void test() {
      List<T> t = met<caret>hod();
    }
  }
}

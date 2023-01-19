// "Make 'method()' return 'java.util.List<T>'" "true-preview"
import java.util.*;

public class Test {
  class Inner<T> {
    void method() {}

    void test() {
      List<T> t = met<caret>hod();
    }
  }
}

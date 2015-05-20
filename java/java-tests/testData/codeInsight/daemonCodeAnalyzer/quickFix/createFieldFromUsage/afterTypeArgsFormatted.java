// "Create field 'field'" "true"
import java.util.*;
class A {
    private Map<String, String> field<caret>;

    void foo(Map<String, String> s){}

  void bar() {
    foo(field);
  }

}


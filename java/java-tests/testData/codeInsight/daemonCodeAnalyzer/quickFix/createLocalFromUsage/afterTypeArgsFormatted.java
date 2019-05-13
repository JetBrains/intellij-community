// "Create local variable 'field'" "true"
import java.util.*;
class A {
  void foo(Map<String, String> s){}

  void bar() {
      Map<String, String> field<caret>;
      foo(field);
  }

}


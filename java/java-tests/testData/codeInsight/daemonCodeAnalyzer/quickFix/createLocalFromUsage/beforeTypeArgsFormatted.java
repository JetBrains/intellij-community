// "Create local variable 'field'" "true"
import java.util.*;
class A {
  void foo(Map<String, String> s){}

  void bar() {
    foo(fie<caret>ld);
  }

}


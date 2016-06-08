// "Change 1st parameter of method 'foo' from 'ArrayList<String>' to 'HashSet<E>'" "false"

import java.util.ArrayList;
import java.util.HashSet;

class InspecitonIssue {

  void foo(ArrayList<String> lst) {}

  void bar() {
    foo(new Hash<caret>Set<>());
  }
}
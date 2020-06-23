// "<html> Change signature of foo(<s>ArrayList&lt;String&gt;</s> <b>HashSet&lt;E&gt;</b>)</html>" "false"

import java.util.ArrayList;
import java.util.HashSet;

class InspecitonIssue {

  void foo(ArrayList<String> lst) {}

  void bar() {
    foo(new Hash<caret>Set<>());
  }
}
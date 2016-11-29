// "<html> Change signature of foo(<s>boolean</s> <b>HashSet&lt;E&gt;</b>)</html>" "false"
import java.util.*;
class IntentionIssue {
  void foo(boolean b) {}
  void foo(ArrayList<String> lst) {}

  void bar() {
    foo(new Hash<caret>Set<>());
  }
}
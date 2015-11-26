// "Replace 'addAll/putAll' method with parametrized constructor call" "false"
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

class C {
  void m() {
    final List<String> strings = new ArrayList<String>();
    Set<String> s = new HashSet<String>();
    s.add(100);
    s.add(101);
    s.add(102);
    string<caret>s.addAll(s);
  }
}
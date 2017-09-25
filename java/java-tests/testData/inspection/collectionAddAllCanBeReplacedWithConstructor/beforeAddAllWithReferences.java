// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

class C {
  void m() {
    Set<String> s = new HashSet<String>();
    s.add(100);
    s.add(101);
    s.add(102);
    final List<String> strings = new ArrayList<String>();
    strings.<caret>addAll(s);
  }
}
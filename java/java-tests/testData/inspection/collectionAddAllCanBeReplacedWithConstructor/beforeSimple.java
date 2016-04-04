// "Replace 'addAll/putAll' method with parametrized constructor call" "true"
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Collection;

class C {
  void m() {
    final Collection<String> strings = new ArrayList<String>();
    string<caret>s.addAll(new HashSet<String>());
  }
}
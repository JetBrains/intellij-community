// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

class C {
  void m() {
    final List<String> strings;
    strings = new ArrayList<String>();
    strings.addAll<caret>(new HashSet<String>());
  }
}
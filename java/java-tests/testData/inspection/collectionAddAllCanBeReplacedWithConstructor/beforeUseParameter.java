// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;

class C {
  void m(String s) {
    final List<String> strings;
    strings = new ArrayList<String>();
    strings.<caret>addAll(Arrays.asList(s, ","));
  }
}
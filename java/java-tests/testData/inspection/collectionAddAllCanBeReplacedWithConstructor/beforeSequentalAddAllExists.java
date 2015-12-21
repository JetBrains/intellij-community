// "Replace 'addAll/putAll' method with parametrized constructor call" "false"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class A {
  void m(String s) {
    final List<String> list = new ArrayList<>();
    list.add<caret>All(Arrays.asList(s, ","));
    list.addAll(Arrays.asList(s, ","));
  }
}
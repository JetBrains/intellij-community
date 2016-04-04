// "Replace 'addAll/putAll' method with parametrized constructor call" "false"
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Arrays;


class C {
  void m() {
    final List<String> list = new ArrayList<>();
    Arrays.asList(1, 2, 3).forEach(x -> list.ad<caret>dAll(Arrays.asList(".", ",")));
  }
}
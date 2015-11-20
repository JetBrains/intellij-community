// "Replace 'addAll/putAll' method with parametrized constructor call" "false"
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Arrays;


class C {
  void m() {
    final List<String> list = new ArrayList<>();
    if (list.addA<caret>ll(Arrays.asList(".", ","))) {
      System.out.println("foo");
    }
  }
}
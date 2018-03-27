// "Replace with Comparator.comparing" "true"

import java.util.Comparator;
public class MyTest {
  void setComparator(Comparator<?> comparator) {
  }

  String getValue(int x) {
    return "";
  }

  {
    setComparator((MyTest o1, <caret>MyTest o2) -> o1.getValue(0).compareTo(o2.getValue(0)));
  }
}
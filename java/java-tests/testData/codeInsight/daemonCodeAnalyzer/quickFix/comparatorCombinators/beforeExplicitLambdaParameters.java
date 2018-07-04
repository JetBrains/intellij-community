// "Replace with Comparator.comparing" "true"

import java.util.Comparator;
public class MyTest {
  void setComparator(Comparator<?> comparator) {
  }

  String getValue() {
    return "";
  }

  {
    setComparator((MyTest o1, <caret>MyTest o2) -> o1.getValue().compareTo(o2.getValue()));
  }
}
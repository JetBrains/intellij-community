// "Replace with Comparator.comparing" "true"

import java.util.Comparator;
public class MyTest {
  void setComparator(Comparator<?> comparator) {
  }

  String getValue(int x) {
    return "";
  }

  {
    setComparator(Comparator.comparing((MyTest o) -> o.getValue(0)));
  }
}
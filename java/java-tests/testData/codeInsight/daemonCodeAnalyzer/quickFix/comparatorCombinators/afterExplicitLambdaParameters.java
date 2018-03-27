// "Replace with Comparator.comparing" "true"

import java.util.Comparator;
public class MyTest {
  void setComparator(Comparator<?> comparator) {
  }

  String getValue() {
    return "";
  }

  {
    setComparator(Comparator.comparing(MyTest::getValue));
  }
}
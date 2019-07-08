import java.util.Collections;
import java.util.List;

class MyTest {
  public MyTest(String s, List<String> l, String s2) {
  }

  public MyTest(String s, int i) {
  }

  {
    new MyTest("", <error descr="Incompatible types. Found: 'java.util.List<java.lang.Object>', required: 'int'">Collections.emptyList()</error>);
  }
}
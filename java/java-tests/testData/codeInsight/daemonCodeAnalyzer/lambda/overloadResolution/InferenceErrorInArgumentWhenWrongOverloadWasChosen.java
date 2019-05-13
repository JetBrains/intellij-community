import java.util.Collections;
import java.util.List;

class MyTest {
  public MyTest(String s, List<String> l, String s2) {
  }

  public MyTest(String s, int i) {
  }

  {
    new MyTest("", <error descr="Incompatible types. Required int but 'emptyList' was inferred to List<T>:
no instance(s) of type variable(s) T exist so that List<T> conforms to Integer">Collections.emptyList()</error>);
  }
}
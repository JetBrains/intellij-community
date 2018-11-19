import java.util.ArrayList;
import java.util.List;

class SomeClass {
  public void test() {
    List<?> objects = new ArrayList<>();
    for (String value : (Iterable<? extends String>) objects) {
      System.out.println(value);
    }
  }
}
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class Main {
  public void test() {
    Collections.sort(new ArrayList<>(), <error descr="Non-static method cannot be referenced from a static context">Comparator::reversed</error>);
  }
}
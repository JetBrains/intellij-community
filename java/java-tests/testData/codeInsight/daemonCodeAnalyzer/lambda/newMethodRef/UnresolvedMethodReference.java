import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class Main {
  public void test() {
    Collections.sort(new ArrayList<>(), Comparator::<error descr="Cannot resolve method 'reversed'">reversed</error>);
  }
}
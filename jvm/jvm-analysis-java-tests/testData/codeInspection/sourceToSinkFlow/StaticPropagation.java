
import org.checkerframework.checker.tainting.qual.Untainted;

import java.util.ArrayList;
import java.util.List;

class StaticPropagation {
  public static void test() {
    sink("1" + System.currentTimeMillis()); //not warn
  }

  public static void sink(@Untainted String string) {

  }
}

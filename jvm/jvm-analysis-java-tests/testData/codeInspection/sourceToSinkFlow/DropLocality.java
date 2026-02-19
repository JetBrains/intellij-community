
import org.checkerframework.checker.tainting.qual.Untainted;

import java.util.ArrayList;
import java.util.List;

class DropLocality {
  public static void test(@Untainted String s1, @Untainted String s2, @Untainted List<String> clean) {
    ArrayList<String> strings1 = new ArrayList<>();
    strings1.add(s1);
    sink(strings1.get(0));

    ArrayList<String> strings2 = new ArrayList<>();
    strings2.add(s1);
    doSomething(strings2);
    sink(<warning descr="Unknown string is used as safe parameter">strings2.get(strings2.size() - 1)</warning>); //warn

    List<String> strings3 = clean;
    doSomething(strings3);
    sink(<warning descr="Unknown string is used as safe parameter">strings3.get(0)</warning>); //warn
  }

  private static void doSomething(List<String> s2) {
    s2.add("1");
  }

  public static void sink(@Untainted String string) {

  }
}

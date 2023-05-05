import javax.annotation.Untainted;

import java.util.ArrayList;
import java.util.List;

class SinkTest {

  public void test(String string) {
    sink(<warning descr="Unknown string is used as safe parameter">string</warning>); //warn
  }

  @Untainted
  public String returnDirty(String dirty) {
    return <warning descr="Unknown string is returned from safe method">dirty</warning>; //warn
  }

  void sink(@Untainted String clear) {

  }

  void assignDirty(@Untainted String clear, String dirty) {
    clear = <warning descr="Unknown string is used as safe parameter">dirty</warning>; //warn
  }

  @Untainted String dirty = <warning descr="Unknown string is used in a safe context">getFromStatic()</warning>; //warn

  static List<String> list = new ArrayList<>();

  private static String getFromStatic() {
    return list.get(0);
  }

  @Untainted
  static String clear = "";

  static void spoil(String dirty) {
    clear = <warning descr="Unknown string is used in a safe context">dirty</warning>; //warn
  }

  static void testLocal(String dirty) {
    @Untainted String clean = <warning descr="Unknown string is assigned to safe variable">dirty</warning>; //warn
  }

  static void testLocal2(String dirty) {
    @Untainted String clean = "";
    clean = <warning descr="Unknown string is assigned to safe variable">dirty</warning>; //warn
  }
}

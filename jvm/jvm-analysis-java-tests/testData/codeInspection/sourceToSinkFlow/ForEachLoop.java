import org.checkerframework.checker.tainting.qual.Untainted;
import java.util.Arrays;
import java.util.List;

class ForEachLoop {
  private static final List<String> CAN_BE_DIRTY = Arrays.asList("select s from Sample s", "select s from Sample s where s.color = 'red'");
  private static final List<String> CLEAN = List.of("select s from Sample s", "select s from Sample s where s.color = 'red'");

  public void testLoopClean() {
    List<String> queries = Arrays.asList("select s from Sample s", "select s from Sample s where s.color = 'red'");
    for (String query : queries) {
      sink(query);
    }
  }

  public void testLoopDirty(String dirty) {
    List<String> queries = Arrays.asList("select s from Sample s", "select s from Sample s where s.color = 'red'", dirty);
    for (String query : queries) {
      sink(<warning descr="Unknown string is used as safe parameter">query</warning>);
    }
  }


  public void testLoopCleanField() {
    for (String query : CLEAN) {
      sink(query);
    }
  }

  public void testLoopDirtyField() {
    for (String query : CAN_BE_DIRTY) {
      sink(<warning descr="Unknown string is used as safe parameter">query</warning>);
    }
  }


  public void sink(@Untainted String clean) {

  }
}

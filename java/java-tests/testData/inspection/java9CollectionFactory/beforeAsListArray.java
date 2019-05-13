// "Replace with 'List.of' call" "true"
import java.util.*;

public class Test {
  public void test() {
    List<String[]> list = Collections.unmodif<caret>iableList(Arrays.<String[]>asList(new String[] {"aaa"}));
  }
}
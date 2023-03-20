// "Fix all 'Excessive lambda usage' problems in file" "true"
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Test extends ArrayList<String> {
  public void test(List<String> list, String replacement) {
    Collections.fill(list, replacement);
    Collections.fill(this, replacement);
  }
}
// "Fix all 'Excessive lambda usage' problems in file" "true"
import java.util.ArrayList;
import java.util.List;

class Test extends ArrayList<String> {
  public void test(List<String> list, String replacement) {
    list.replaceAll(ignored <caret>-> replacement);
    super.replaceAll(ignored -> replacement);
  }
}
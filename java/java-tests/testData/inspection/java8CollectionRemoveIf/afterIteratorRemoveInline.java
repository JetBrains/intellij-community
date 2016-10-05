// "Replace the loop with Collection.removeIf" "true"
import java.util.Iterator;
import java.util.List;

public class Main {
  public void testIterator(List<List<String>> data, boolean b) {
      data.removeIf(strings -> strings.isEmpty() && b);
  }
}
// "Replace the loop with 'Collection.removeIf'" "false"
import java.util.Iterator;
import java.util.List;

public class Main {
  public void testIterator(List<List<String>> data, boolean b) {
    for<caret>(Iterator<List<String>> iter = data.iterator(); iter.hasNext();) {
      if(b && iter.next().isEmpty()) {
        iter.remove();
      }
    }
  }
}
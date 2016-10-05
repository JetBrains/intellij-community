// "Replace the loop with Collection.removeIf" "true"
import java.util.Iterator;
import java.util.List;

public class Main {
  public void testIterator(List<List<String>> data, boolean b) {
    for(Ite<caret>rator<List<String>> iter = data.iterator(); iter.hasNext();) {
      if(iter.next().isEmpty() && b) {
        iter.remove();
      }
    }
  }
}
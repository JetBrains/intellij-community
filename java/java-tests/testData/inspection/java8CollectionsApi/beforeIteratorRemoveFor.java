// "Replace the loop with Collection.removeIf" "true"
import java.util.*;

public class Main {
  public void removeEmpty(List<String> list) throws Exception {
    f<caret>or(Iterator<String> it = list.iterator(); it.hasNext();) {
      String str = it.next();
      if(str.isEmpty()) {
        it.remove();
      }
    }
  }
}
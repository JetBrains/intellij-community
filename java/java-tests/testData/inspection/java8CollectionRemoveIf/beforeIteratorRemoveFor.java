// "Replace the loop with Collection.removeIf" "true"
import java.util.*;

public class Main {
  public void removeEmpty(List<String> list) throws Exception {
    f<caret>or(Iterator<String> it = list.iterator(); it.hasNext();) {
      // iterate over list
      String str = it.next();
      // if it's empty
      if(str.isEmpty()) {
        /* remove! */
        it.remove();
      }
    }
  }
}
// "Replace the loop with Collection.removeIf" "false"
import java.util.*;

public class Main {
  public void removeEmpty(List<String> list) throws Exception {
    Iterator<String> it = list.iterator();
    while<caret>(it.hasNext()) {
      String str = it.next();
      // remove empty
      if(str.isEmpty()) {
        it.remove();
      }
    }
    it = list.iterator();
  }
}
// "Replace the loop with Collection.removeIf" "true"
import java.util.*;

public class Main {
  public void removeEmpty(List<String> list) throws Exception {
      // iterate over list
// if it's empty
/* remove! */
      list.removeIf(String::isEmpty);
  }
}
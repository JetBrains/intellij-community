// "Replace with findFirst()" "true"

import java.util.Collection;
import java.util.List;

public class Main {
  public static String find(List<List<String>> list) {
    if(list != null) {
        return list.stream().flatMap(Collection::stream).filter(string -> string.startsWith("ABC")).findFirst().orElse(null);
    }
    return null;
  }
}
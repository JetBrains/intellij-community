// "Replace with findFirst()" "true"

import java.util.Collection;
import java.util.List;

public class Main {
  public static String find(List<List<String>> list) {
    if(list == null) {
      System.out.println("oops");
      return "";
    } else {
        return list.stream().flatMap(Collection::stream).filter(string -> string.startsWith("ABC")).findFirst().orElse(null);
    }
  }
}
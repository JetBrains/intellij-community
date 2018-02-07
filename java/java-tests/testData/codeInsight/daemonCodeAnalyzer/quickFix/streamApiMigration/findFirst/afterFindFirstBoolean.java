// "Replace with findFirst()" "true"

import java.util.Collection;
import java.util.List;

public class Main {
  public static boolean find(List<List<String>> list) {
    /*
          Block comment
           */
      return list.stream().flatMap(Collection::stream).filter(string -> string.startsWith("ABC")).findFirst().filter(string -> string.substring(3).equals("xyz")).isPresent();
  }
}
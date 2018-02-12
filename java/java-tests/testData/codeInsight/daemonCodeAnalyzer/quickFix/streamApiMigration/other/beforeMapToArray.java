// "Replace with toArray" "true"
import java.util.*;

public class Main {
  public String[] test(Map<String, String> map) {
    String[] array = new String[map.size()];
    for(int<caret> i=0; i<map.keySet().size(); i++) {
      array[i] = String.valueOf(i);
    }
    return array;
  }
}
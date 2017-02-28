// "Replace with 'Map.of' call" "true"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap;
      myMap = Map.<String, String>of("a", "b", "c", "b");
  }
}
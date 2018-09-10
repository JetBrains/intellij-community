// "Replace with toArray" "true"
import java.util.*;
import java.util.stream.IntStream;

public class Main {
  public String[] test(Map<String, String> map) {
    String[] array = IntStream.range(0, map.size()).mapToObj(String::valueOf).toArray(String[]::new);
      return array;
  }
}
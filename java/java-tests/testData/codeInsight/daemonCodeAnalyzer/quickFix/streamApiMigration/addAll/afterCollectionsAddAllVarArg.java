// "Replace with toArray" "true"
import java.util.*;
import java.util.stream.Stream;

public class Test {
  Object[] test(List<String> list) {
      return list.stream().filter(Objects::nonNull).flatMap(str -> Stream.of(str, str + str)).sorted().toArray();
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(new Test().test(Arrays.asList("a", "b", "ba", "x", null, "c"))));
  }
}

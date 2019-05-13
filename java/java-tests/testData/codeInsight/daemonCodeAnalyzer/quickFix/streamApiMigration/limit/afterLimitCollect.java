// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  private List<String> test(String[] list, int limit) {
    List<String> result;
    List<String> other = new ArrayList<>();
    System.out.println("hello");
      result = Arrays.stream(list).filter(Objects::nonNull).limit(limit).map(s -> s + s).sorted().collect(Collectors.toList());
      return result;
  }
}

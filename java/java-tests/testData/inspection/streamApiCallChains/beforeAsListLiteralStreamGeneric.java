// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public static void main(String[] args) {
    List<List<Object>> list = Arrays.<List<Object>>asList(Arrays.asList(1,2,3), Arrays.asList(1.0, 2.0, 3.0)).str<caret>eam()
      .collect(Collectors.toList());
  }
}

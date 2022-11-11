// "Replace with collect" "true-preview"
import java.util.*;
import java.util.stream.Collectors;

public class Test {
  List<String> test(int[] repeats) {
    List<String> result = Arrays.stream(repeats).mapToObj(val -> Collections.nCopies(val, String.valueOf(val))).flatMap(Collection::stream).collect(Collectors.toList());
      return result;
  }
}

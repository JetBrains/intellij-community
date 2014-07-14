import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

class Test {

  public static void main(String[] args) {
    Arrays.asList("these", "are", "some", "words").stream()
      .collect(Collectors.toMap(Function.identity(), (s) -> 1, Integer::sum));
  }

}

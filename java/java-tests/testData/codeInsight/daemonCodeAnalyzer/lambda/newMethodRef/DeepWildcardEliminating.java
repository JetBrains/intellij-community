import java.util.Arrays;
import java.util.List;

class FunctionReferenceTest {
  public static void main(String[] args) {
    List<String> strings = Arrays.asList();

    int sum = strings.stream().mapToInt(CharSequence::length).sum();

    List<? extends CharSequence> sequences = strings;

    sum = sequences.stream().mapToInt(CharSequence::length).sum();
  }
}

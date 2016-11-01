// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> test(List<List<String>> list) {
    OUTER:
    for(int i=0; i<10; i++) {
        Optional<String> found = Optional.empty();
        OUTER1:
        for (List<String> x : list) {
            if (x != null) {
                for (String s : x) {
                    found = Optional.of(s);
                    break OUTER1;
                }
            }
        }
        return found.orElse("");
    }
    return null;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
  }
}
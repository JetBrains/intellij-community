// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> test(List<List<String>> list) {
    String res = null;
    OUTER:
    for(int i=0; i<10; i++) {
        String found = "";
        OUTER1:
        for (List<String> x : list) {
            if (x != null) {
                for (String s : x) {
                    found = s;
                    break OUTER1;
                }
            }
        }
        res = found;
    }
    return res;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
  }
}
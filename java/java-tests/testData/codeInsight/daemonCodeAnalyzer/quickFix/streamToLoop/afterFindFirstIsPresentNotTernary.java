// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Objects;

public class Main {
  String test(List<List<String>> strings) {
      for (List<String> string : strings) {
          if (Objects.nonNull(string)) {
              for (String s : string) {
                  return "abc";
              }
          }
      }
      return "xyz";
  }
}

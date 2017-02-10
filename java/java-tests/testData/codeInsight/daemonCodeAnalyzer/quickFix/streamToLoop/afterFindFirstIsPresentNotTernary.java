// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Objects;

public class Main {
  String test(List<List<String>> strings) {
      for (List<String> string : strings) {
          if (string != null) {
              for (String s : string) {
                  return "abc";
              }
          }
      }
      return "xyz";
  }
}

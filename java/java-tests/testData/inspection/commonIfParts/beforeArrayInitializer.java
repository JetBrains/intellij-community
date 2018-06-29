// "Extract common part with variables from if " "true"

import java.util.List;
import java.util.Map;

public class Main {
  public static void main(String... args) {
    int i = 1;
    if<caret> (i % 2 == 0) {
      String[] array = {};
      main(array);
    } else {
      String[] array = {"not empty"};
      main(array);
    }
  }
}
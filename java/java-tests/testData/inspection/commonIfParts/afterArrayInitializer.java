// "Extract common part with variables from if " "true"

import java.util.List;
import java.util.Map;

public class Main {
  public static void main(String... args) {
    int i = 1;
      String[] array;
      if (i % 2 == 0) {
          array = new String[]{};
      } else {
          array = new String[]{"not empty"};
      }
      main(array);
  }
}
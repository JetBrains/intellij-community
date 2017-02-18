// "Replace with findFirst()" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  public String test(String[] array) {
      return Arrays.stream(array).limit(11).filter(Objects::nonNull).findFirst().orElse("");
  }
}
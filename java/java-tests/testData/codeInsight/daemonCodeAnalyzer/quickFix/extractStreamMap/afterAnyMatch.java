// "Extract variable 'lowerCase' to separate stream step" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  boolean test(List<String> list) {
    return list.stream().map(String::toLowerCase)
      .anyMatch(lowerCase -> "test".equals(lowerCase));
  }
}
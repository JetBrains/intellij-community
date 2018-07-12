// "Extract variable 'lowerCase' to 'map' operation" "false"
import java.util.*;
import java.util.stream.*;

public class Test {
  boolean test(List<String> list) {
    return list.stream()
      .anyMatch(s -> {
        System.out.println(s);
        String <caret>lowerCase = s.toLowerCase();
        return "test".equals(lowerCase);
      });
  }
}
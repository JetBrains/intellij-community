// "Disable 'Extract to separate mapping method'" "false"
import java.util.*;
import java.util.stream.*;

public class Test {
  boolean testWrong(List<String> list) {
    return list.stream()
      .anyMatch(s -> {
        String <caret>lowerCase = s.toLowerCase();
        return lowerCase.equals(s);
      });
  }
}
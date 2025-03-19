import java.util.function.*;

class InlineTest {
  Predicate<String> getPredicate(int value) {
    return str -> str.length() > value && <caret>checkString(str);
  }

  boolean checkString(String value) {
    return value.startsWith("prefix") && value.endsWith("suffix");
  }
}
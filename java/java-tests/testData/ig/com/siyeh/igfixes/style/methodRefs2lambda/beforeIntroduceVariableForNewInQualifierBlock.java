// "Replace method reference with lambda" "true-preview"
import java.util.*;

class Test {
  boolean result = Arrays.asList("a", "a").stream().allMatch(new <caret>HashSet<>()::add);
}
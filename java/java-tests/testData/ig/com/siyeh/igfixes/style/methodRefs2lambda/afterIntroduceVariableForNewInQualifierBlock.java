// "Replace method reference with lambda" "true-preview"
import java.util.*;

class Test {
  boolean result;

    {
        HashSet<Object> objects = new HashSet<>();
        result = Arrays.asList("a", "a").stream().allMatch(e -> objects.add(e));
    }
}
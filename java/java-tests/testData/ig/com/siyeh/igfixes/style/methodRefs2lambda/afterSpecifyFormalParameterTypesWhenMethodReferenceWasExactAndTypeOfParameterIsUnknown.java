// "Replace method reference with lambda" "true-preview"
import java.util.*;
class Test {
  {
    Comparator.comparing(//end of line comment
            (String s) -> s.trim());
  }
}
// "Replace method reference with lambda" "true-preview"
import java.util.*;
class Test {
  {
    Comparator.comparing(String//end of line comment
                           ::tr<caret>im);
  }
}
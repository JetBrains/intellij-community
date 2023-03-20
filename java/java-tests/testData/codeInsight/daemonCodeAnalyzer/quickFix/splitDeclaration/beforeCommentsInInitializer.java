// "Split into declaration and assignment" "true-preview"
import java.util.*;
class Test {
  {
    List<String> l <caret>= new ArrayList//end line comment
    <>();
  }
}
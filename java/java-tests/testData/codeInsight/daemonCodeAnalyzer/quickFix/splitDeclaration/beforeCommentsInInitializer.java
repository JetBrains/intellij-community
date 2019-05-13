// "Split into declaration and assignment" "true"
import java.util.*;
class Test {
  {
    List<String> l <caret>= new ArrayList//end line comment
    <>();
  }
}
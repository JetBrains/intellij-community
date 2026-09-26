// "Replace explicit type with 'var'" "true"
import java.util.*;

class X {
  
  void x() {
    <caret>ArrayList<String> list = new ArrayList<>() {{
      add("one");
      add("two");
    }};
  }
}
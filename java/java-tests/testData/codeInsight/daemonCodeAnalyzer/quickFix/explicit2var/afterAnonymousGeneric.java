// "Replace explicit type with 'var'" "true"
import java.util.*;

class X {
  
  void x() {
      var list = new ArrayList<String>() {{
      add("one");
      add("two");
    }};
  }
}
// "Replace 'var' with explicit type" "true-preview"
import java.util.*;

class X {
  
  void x() {
    var<caret> x = new ArrayList<String>();
  }
}
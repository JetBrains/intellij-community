// "Add explicit type arguments" "true-preview"
import java.util.*;

class Test {
    <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(boolean b) { 
      List<String> s = b ? this.<String>f() : new ArrayList<String>(); 
    }
}
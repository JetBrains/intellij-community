// "Add explicit type arguments" "true-preview"
import java.util.*;

class Test {
    static <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(boolean b) { 
      List<String> s = b ? Test.<String>f() : new ArrayList<String>(); 
    }
}
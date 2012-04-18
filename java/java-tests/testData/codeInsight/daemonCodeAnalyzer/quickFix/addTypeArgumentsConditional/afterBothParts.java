// "Add explicit type arguments" "true"
import java.util.*;

class Test {
    static <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(boolean b) { 
      List<String> s = b ? Test.<String>f() : f(); 
    }
}
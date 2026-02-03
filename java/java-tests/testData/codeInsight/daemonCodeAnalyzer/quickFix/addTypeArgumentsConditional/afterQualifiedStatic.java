// "Add explicit type arguments to else-branch call" "true-preview"
import java.util.*;

class Test {
    static <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(Test t, boolean b) { 
      List<String> s = b ? new ArrayList<String>() : t.<String>f(); 
    }
}
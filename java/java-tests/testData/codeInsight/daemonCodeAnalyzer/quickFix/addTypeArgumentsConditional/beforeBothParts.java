// "Add explicit type arguments to then-branch call" "true-preview"
import java.util.*;

class Test {
    static <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(boolean b) { 
      List<String> s = b ? f<caret>() : f(); 
    }
}
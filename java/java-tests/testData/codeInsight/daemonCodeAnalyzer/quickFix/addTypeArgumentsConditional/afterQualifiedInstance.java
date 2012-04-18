// "Add explicit type arguments" "true"
import java.util.*;

class Test {
    <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(Test t, boolean b) { 
      List<String> s = b ? t.<String>f() : new ArrayList<String>(); 
    }
}
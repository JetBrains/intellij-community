// "Add explicit type arguments" "true"
import java.util.*;

class Test {
    static <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(Test t, boolean b) { 
      List<String> s = b ? t.f<caret>() : new ArrayList<String>(); 
    }
}
// "Add explicit type arguments to then-branch call" "true-preview"
import java.util.*;

class Test {
    <T> List<T> f() { return new ArrayList<T>(); }
    void someMethod(Test t, boolean b) { 
      List<String> s = b ? (t.f<caret>()) : new ArrayList<String>(); 
    }
}
// "Add explicit type arguments" "true-preview"
import java.util.*;
class Test {
    <T> List<T> f() { return new ArrayList<T>(); }
    void g(List<Integer> a) {}
    void someMethod() { g(f<caret>()); }
}
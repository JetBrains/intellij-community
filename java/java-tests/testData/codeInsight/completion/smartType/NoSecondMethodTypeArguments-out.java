import java.util.Collections;
import java.util.List;

class A {
    void foo(List<String> x){}
    {
        foo(Collections.<String>emptyList());<caret>
    }
}
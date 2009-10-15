import java.util.Collections;
import java.util.List;

public class SomeClass {
    void foo(List<String> l) {

    }

    {
        foo(Collections.<String>emptyList());<caret>
    }

}

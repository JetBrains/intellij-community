import java.util.Collections;
import java.util.Map;

public class SomeClass {
    void foo(Map<String, String> l) {

    }

    {
        foo(Collections.singletonMap(<caret>));
    }

}

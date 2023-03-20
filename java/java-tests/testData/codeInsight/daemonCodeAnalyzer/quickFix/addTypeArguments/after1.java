// "Add explicit type arguments" "true-preview"

import java.util.List;
import java.util.Collections;

class Example {
    void f(List<String> list) {}

    void g() {
           f(<caret>Collections.<String>emptyList());
    }
}


// "Add explicit type arguments" "true"

import java.util.List;
import java.util.Collections;

class Example {
    void f(List<String> list) {}

    void g() {
           f(<caret>Collections.emptyList());
    }
}


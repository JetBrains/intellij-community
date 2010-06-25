// "Add explicit type arguments" "true"

import java.util.List;
import java.util.Collections;

class Example {
    Example(List<String> list) {}

    void g() {
           new Example(<caret>Collections.<String>emptyList());
    }
}


// "Add explicit type arguments" "true-preview"

import java.util.List;
import java.util.Collections;

class Example {
    Example(List<String> list) {}

    void g() {
           new Example(<caret>Collections.emptyList());
    }
}


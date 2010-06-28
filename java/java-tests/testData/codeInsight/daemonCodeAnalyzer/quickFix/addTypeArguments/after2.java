// "Add explicit type arguments" "true"

import java.util.Collection;
import java.util.Collections;

class Example {
    void f(Collection<String> list) {}

    void g() {
           f(<caret>Collections.<String>emptyList());
    }
}


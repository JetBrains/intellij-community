// "Cast argument to 'Map<Foo, Bar>'" "false"

import java.util.*;

class X {
    void run(Foo single) {}
    void run(Map<Foo, Bar> map) {}

    void test(Bar bar) {
        run(Collections.<caret>singletonMap(getFoo(), bar));
    }

    native Foo getFoo();
}
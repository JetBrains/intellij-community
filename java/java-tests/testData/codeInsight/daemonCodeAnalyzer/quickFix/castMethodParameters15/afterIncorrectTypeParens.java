// "Cast argument to 'Map<Foo, Bar>'" "true-preview"

import java.util.*;

class X {
    void run(Foo single) {}
    void run(Map<Foo, Bar> map) {}

    void test(Bar bar) {
        run((Map<Foo, Bar>) Collections.singletonMap(getFoo(), bar));
    }

    native Foo getFoo();
}
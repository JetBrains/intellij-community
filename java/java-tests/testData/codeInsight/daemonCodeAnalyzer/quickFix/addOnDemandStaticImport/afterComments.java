// "Add on demand static import for 'java.util.Arrays'" "true"
import java.util.*;

import static java.util.Arrays.*;

class Foo {
    void test(String[] foos, String[] bars) {
        System.out.println(/*foos1*//*foos2*/asList(foos)+":"+ /*bars1*//*bars2*/asList(bars));
    }

    void test2(String[] foos, String[] bars) {
        System.out.println(/*foos1*//*foos2*/asList(foos)+":"+ /*bars1*//*bars2*/asList(bars));
    }
}
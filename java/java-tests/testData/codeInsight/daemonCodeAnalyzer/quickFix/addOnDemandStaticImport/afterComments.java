// "Add on demand static import for 'java.util.Arrays'" "true"
import java.util.*;

import static java.util.Arrays.*;

class Foo {
    void test(String[] foos, String[] bars) {
        System.out.println(/*foos1*//*foos2*/asList(foos)+":"+/*bars1*//*bars2*/asList(bars));
    }

    void test2(String[] foos, String[] bars) {
        System.out.println(/*foos0*//*foos1*//*foos2*/asList(foos)+":"+/*bars0*//*bars1*//*bars2*/asList(bars));
    }

    void test3(String[] foos, String[] bars) {
        System.out.println(//line comment
                asList(foos)+":"+//line comment
                        asList(bars));
    }
}
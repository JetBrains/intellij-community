// "Add on demand static import for 'java.util.Arrays'" "true"
import java.util.*;

class Foo {
    void test(String[] foos, String[] bars) {
        System.out.println(<caret>Arrays/*foos1*/./*foos2*/asList(foos)+":"+Arrays/*bars1*/./*bars2*/asList(bars));
    }

    void test2(String[] foos, String[] bars) {
        System.out.println(/*foos0*/Arrays/*foos1*/./*foos2*/asList(foos)+":"+/*bars0*/Arrays/*bars1*/./*bars2*/asList(bars));
    }

    void test3(String[] foos, String[] bars) {
        System.out.println(Arrays.//line comment
                           asList(foos)+":"+Arrays.//line comment
                                                    asList(bars));
    }
}
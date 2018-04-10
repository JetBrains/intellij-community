// "Add on demand static import for 'java.util.Arrays'" "true"
import java.util.Arrays;

import static java.util.Arrays.*;

public class MyFile {
    void test() {
        System.out.println(/*1*//*2*/asList(/*3*//*4*/asList("foo", /*5*/ /*6*/ asList("bar", "baz")), /*7*/ /*8*/ asList("qux")));
    }
}

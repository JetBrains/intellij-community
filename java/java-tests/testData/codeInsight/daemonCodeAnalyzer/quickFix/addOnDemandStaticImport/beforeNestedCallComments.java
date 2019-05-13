// "Add on demand static import for 'java.util.Arrays'" "true"
import java.util.Arrays;

public class MyFile {
    void test() {
        System.out.println(Ar<caret>rays/*1*/./*2*/asList(Arrays/*3*/./*4*/asList("foo", Arrays/*5*/./*6*/asList("bar", "baz")), Arrays/*7*/./*8*/asList("qux")));
    }
}

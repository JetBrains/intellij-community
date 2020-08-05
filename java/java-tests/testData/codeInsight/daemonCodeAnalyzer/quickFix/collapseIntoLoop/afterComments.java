import java.util.Arrays;

// "Collapse into loop" "true"
class X {
    void test() {
        // 1
        for (String s : Arrays.asList("Hello", "Hello" +/*2.1*/"world", "Hello1")) {
            System.out.println(s);
        }/*2.0*/
        // 2
        // 3
        // 4
    }
}
// "Replace method call on lambda with lambda body" "true"

import java.util.function.Function;

public class Test {
    public static void main(String[] args) {
        String s = ((Function<String, String>) ((x) -> {
            /* comment1 */

            System.out.println(/* output x */x);

            // comment2
            System.out.println("hello");
            return /*in return */ "foo" + //inline
                                  "bar";
        })).<caret>apply("a"+/* who-hoo */ "x");
    }
}
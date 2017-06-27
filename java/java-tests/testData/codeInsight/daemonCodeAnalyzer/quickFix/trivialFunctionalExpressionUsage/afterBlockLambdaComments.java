// "Replace method call on lambda with lambda body" "true"

import java.util.function.Function;

public class Test {
    public static void main(String[] args) {
        /* comment1 */

        System.out.println(/* output x */"a"+/* who-hoo */ "x");

        // comment2
        System.out.println("hello");
        /*in return */
        String s = "foo" + //inline
                              "bar";
    }
}
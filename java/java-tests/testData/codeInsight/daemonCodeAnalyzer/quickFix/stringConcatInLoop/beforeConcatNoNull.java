// "Convert variable 's1' from String to StringBuilder (null-safe)" "false"

import java.util.List;

class Test {
    static void test(List<String> list) {
        String s2 = "bar";

        String s1 = s2;

        for (String s : list) {
            s1 +<caret>= "baz";
        }

        s1 += "foo";
        System.out.println(s1);
    }
}

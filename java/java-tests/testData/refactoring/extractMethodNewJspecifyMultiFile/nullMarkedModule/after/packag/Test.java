package packag;

import org.jspecify.annotations.Nullable;

public class Test {
    void test(String s) {
        String s1 = System.getProperty("foo");


        String result = newMethod(s, s1);


        System.out.println(result.trim());
    }

    private String newMethod(String s, @Nullable String s1) {
        String s2 = s.trim();
        String s3 = (s1 == null ? "" : s1).trim();
        String result = s2 + s3;
        return result;
    }
}

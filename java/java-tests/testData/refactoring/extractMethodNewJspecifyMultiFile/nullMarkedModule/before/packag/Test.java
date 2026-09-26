package packag;

public class Test {
    void test(String s) {
        String s1 = System.getProperty("foo");

        <selection>
        String s2 = s.trim();
        String s3 = (s1 == null ? "" : s1).trim();
        String result = s2 + s3;
        </selection>

        System.out.println(result.trim());
    }
}

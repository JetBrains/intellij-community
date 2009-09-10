public class A {
    void test(String s1, final String <caret>s2) {
        System.out.println(s1);
        System.out.println(s2);
    }

    void callTest() {
        String s = "";
        test(s, s);
    }
}

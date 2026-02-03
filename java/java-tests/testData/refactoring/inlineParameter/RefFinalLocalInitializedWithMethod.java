public class A {
    void test(String s, int <caret>l) {
        System.out.println(s);
        System.out.println(l);
    }

    void callTest(String aString) {
        final int len = aString.length();
        test(aString, len);
    }
}

public class A {
    void test(String s, int <caret>l) {
        System.out.println(s);
        System.out.println(l);
    }

    void callTest() {
        String aString = "abc";
        test(aString, aString.length());
    }
}

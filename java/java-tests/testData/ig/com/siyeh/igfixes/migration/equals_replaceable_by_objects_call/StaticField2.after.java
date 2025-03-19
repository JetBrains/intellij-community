import java.util.Objects;

class T {
    static class A {
        static String a;
    }
    static boolean dfferent(String s) {
        return !Objects.equals(A.a, s);
    }
}
import java.util.*;
class Main {
    public static <T> T foo() {return null;}

    public static <B extends A, A> void bar0(B b, A a) {}
    public static <B extends A, A> void bar(A a, B b) {}
    public static <B extends List<A>, A> void bar1(B b, A a) {}
    public static <B extends Integer, A> void bar2(B b, A a) {}
    public static <B extends C, A, C> void bar3(B b, A a) {}
    static {
        bar0(foo(), "");
        bar0("", foo());

        bar("", foo());
        bar(foo(), "");

        bar1(foo(), "");
        bar2(foo(), "");
        bar3(foo(), "");
    }
}
public class MyTest {

    interface I { void foo(int i); }

    static void print(int i) {
        System.out.println(i);
    }

    static {
        I sam = MyTest:<caret>:print;
    }
}
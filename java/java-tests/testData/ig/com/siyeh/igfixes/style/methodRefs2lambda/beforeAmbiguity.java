// "Replace method reference with lambda" "true-preview"
public class MyTest {

    static void m(Integer i) { assertTrue(true); }

    interface I1 {
        void m(int x);
    }

    interface I2 {
        void m(Integer x);
    }

    static void call(Integer i, I1 s) {}
    static void call(int i, I2 s) {}

    public static void main(String[] args) {
        call(1, MyTest:<caret>:m);
    }
}

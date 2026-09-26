// "Replace method reference with lambda" "true-preview"
public class MyTest<X> {

    MyTest(X x) {}

    interface I<Z> {
        MyTest<Z> m(Z z);
    }

    static <Y> void test(I<Y> s, Y arg) {
        s.m(arg);
    }

    static {
        I<String> s = MyTest<String>:<caret>:new;
    }
}
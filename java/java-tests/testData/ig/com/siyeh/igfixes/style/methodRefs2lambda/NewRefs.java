public class MyTest {

    MyTest() {}

    interface I {
        MyTest m();
    }

    static void test(I i) {
        i.m();
    }

    static {
        I i = MyTest:<caret>:new;
    }
}

// "Replace method reference with lambda" "true-preview"
public class MyTest {

    interface I {
        MyTest m();
    }

    static void test(I i) {
        i.m();
    }

    static {
        I i = () -> new MyTest();
    }
}

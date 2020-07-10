// "Create method 'test'" "true"
public class Test {
    void test(boolean b, Object obj) {
        if (b || test() == obj) {
            // do smth
        }
    }

    private Object test() {
        return null;
    }
}

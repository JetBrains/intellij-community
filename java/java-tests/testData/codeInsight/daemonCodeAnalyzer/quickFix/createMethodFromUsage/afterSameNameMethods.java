// "Create method 'test'" "true"
public class Test {
    public Test(Impl impl) {
        impl.test(test());
    }

    private String test() {
        return null;
    }
}

class Impl {
    void test(String s) {}
}
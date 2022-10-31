// "Create method 'test'" "true-preview"
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
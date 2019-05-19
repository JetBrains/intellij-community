// "Create method 'test'" "true"
public class Test {
    public Test(Impl impl) {
        impl.test(te<caret>st());
    }
}

class Impl {
    void test(String s) {}
}
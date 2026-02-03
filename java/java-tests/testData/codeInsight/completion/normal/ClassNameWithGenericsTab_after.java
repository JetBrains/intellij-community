public class TestClass {

    public TestClass create() {
        final int value = 1;
        return new Xxx<caret><String>(value);
    }
}

class Xxx { }
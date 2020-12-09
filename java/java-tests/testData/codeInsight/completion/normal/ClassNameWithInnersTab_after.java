public class TestClass {

    public TestClass create() {
        final int value = 1;
        return new Xxx(<caret>value);
    }
}

class Xxx {
    Xxx(String x) {
    }

    static class Yyy {

    }
}
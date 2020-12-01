public class TestClass {

    public TestClass create() {
        final int value = 1;
        return new Xx<caret>TexCompXxx(value);
    }
}

class Xxx {
    Xxx(String x) {
    }

    static class Yyy {

    }
}
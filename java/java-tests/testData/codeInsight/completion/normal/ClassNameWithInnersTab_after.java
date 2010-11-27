public class TestClass {

    public TestClass create() {
        final int value = 1;
        return new Xxx<caret>(value);
    }
}

class Xxx {
    private Xxx(String x) {
    }

    class Yyy {

    }
}
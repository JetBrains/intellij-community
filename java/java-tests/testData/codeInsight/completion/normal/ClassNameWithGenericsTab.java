public class TestClass {

    public TestClass create() {
        final int value = 1;
        return new Xx<caret>TexCompXxx<String>(value);
    }
}

class Xxx { }
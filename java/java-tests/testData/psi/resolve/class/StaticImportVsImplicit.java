import static Outer.Double;

class Outer {
    static class Double {
    }
}

class StaticImportTest {
    public static void main(String[] args) {
        <caret>Double d;
    }
}
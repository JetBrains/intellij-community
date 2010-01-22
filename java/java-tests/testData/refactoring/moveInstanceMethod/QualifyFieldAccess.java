public class Test {

    private Bar bar = new Bar();

    public void <caret>foo(int x) {
        bar.x = x;
    }

    private static class Bar {
        private int x;
    }
}

public class Test {

    private Bar bar = new Bar();

    public void <caret>foo(int y) {
        bar.x = y;
    }

    private static class Bar {
        private int x;
    }
}

class Foo {
    public int x;

    public void foo() {
        int y = 0;
        y = x + 1;
    }

    public class Inner {
        public int <caret>z;

        public void foo() {
            z = new Foo().x + 1;
            z = x + 1;
        }
    }
}

class Foo {
    public int x;

    public void foo() {
        int y = 0;
        y = x + 1;
    }

    public class Inner {
        public int <caret>x;

        public void foo() {
            x = new Foo().x + 1;
            x = Foo.this.x + 1;
        }
    }
}

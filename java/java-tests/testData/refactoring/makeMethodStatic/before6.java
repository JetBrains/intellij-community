public class Foo extends Bar {
    public int i;
    public int <caret>method() {
        return super.i;
    }
}
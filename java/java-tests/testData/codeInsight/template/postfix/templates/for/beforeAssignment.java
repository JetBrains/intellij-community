public class Foo {
    void m() {
        int[] xs = {1, 2, 3};
        xs.for<caret>
        xs = new int[0];
    }
}
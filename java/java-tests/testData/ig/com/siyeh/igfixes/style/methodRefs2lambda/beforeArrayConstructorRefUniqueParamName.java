// "Replace method reference with lambda" "true-preview"
public class Foo {
    public void test() {
        Object i = null;
        long[][] avg = collect(long[][]:<caret>:new);
    }

    interface P<T> {
        T _(int i);
    }

    <T> T collect(P<T> h) {
        return null;
    }
}
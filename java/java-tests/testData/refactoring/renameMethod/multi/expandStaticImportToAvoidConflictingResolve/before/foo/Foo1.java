package foo;
import static bar.Bar.createBar;
public class Foo {
    protected static Object bar(int i) {
        return createBar();
    }

    static void bar(int i, int j) {}
}
package foo;
import bar.Bar;

public class Foo {
    protected static Object bar(int i) {
        return Bar.bar();
    }

    static void bar(int i, int j) {}
}
package foo;
import static bar.Bar.createBar;
public class Foo {
    protected static Object bar() {
        return createBar();
    }
}
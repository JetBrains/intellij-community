import java.lang.AutoCloseable

public class Foo {
    void m() {
        getStream().twr <caret>
    }

    Object getStream()
    {
        return null;
    }
}
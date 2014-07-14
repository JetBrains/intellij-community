import java.lang.AutoCloseable

public class Foo {
    void m() {
        try (AutoCloseable stream = getStream()) {
            <caret>
        } catch (java.lang.Exception e) {
        }
    }

    AutoCloseable getStream()
    {
        return null;
    }

    private class Exception extends java.lang.Exception
    {

    }
}
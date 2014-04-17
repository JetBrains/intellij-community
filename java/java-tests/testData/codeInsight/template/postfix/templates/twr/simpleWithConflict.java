import java.lang.AutoCloseable

public class Foo {
    void m() {
        getStream().twr<caret>
    }

    AutoCloseable getStream()
    {
        return null;
    }

    private class Exception extends java.lang.Exception
    {

    }
}
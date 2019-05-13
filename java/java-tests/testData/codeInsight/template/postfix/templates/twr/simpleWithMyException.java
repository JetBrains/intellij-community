import java.lang.AutoCloseable
import java.lang.Exception;

public class Foo {
    void m() {
        getStream().twr<caret>
    }

    MyStream getStream()
    {
        return null;
    }

    private class MyStream implements AutoCloseable
    {
        public void close() throws MyException {}
    }

    private class MyException extends Exception
    {

    }
}
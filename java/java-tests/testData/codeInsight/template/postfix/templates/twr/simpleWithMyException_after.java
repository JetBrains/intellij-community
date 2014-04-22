import java.lang.AutoCloseable
import java.lang.Exception;

public class Foo {
    void m() {
        try (MyStream stream = getStream()) {
            <caret>
        } catch (MyException e) {
        }
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
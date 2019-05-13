import java.lang.AutoCloseable

public class Foo {
    void m() {
        try (AutoCloseable stream = getStream()) {
            <caret>
        } catch (Exception e) {
        }
    }

    AutoCloseable getStream()
    {
        return null;
    }
}
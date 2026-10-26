import java.io.IOException;
import java.io.UncheckedIOException;

class Test {
    String test(boolean b) {
        if (b) {
            return "";
        }
        <selection>try {
            throw new IOException();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }</selection>
    }
}

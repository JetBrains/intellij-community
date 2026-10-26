import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

class Test {
    String test(boolean b) {
        if (b) {
            return "";
        }
        throw newMethod();
    }

    private @NotNull UncheckedIOException newMethod() {
        try {
            throw new IOException();
        } catch (IOException e) {
            return new UncheckedIOException(e);
        }
    }
}

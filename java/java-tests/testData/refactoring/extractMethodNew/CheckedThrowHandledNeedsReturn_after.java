import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class Test {
    String test(boolean b) throws IOException {
        if (b) {
            return "";
        }
        throw newMethod();
    }

    private @NotNull IOException newMethod() {
        return new IOException();
    }
}

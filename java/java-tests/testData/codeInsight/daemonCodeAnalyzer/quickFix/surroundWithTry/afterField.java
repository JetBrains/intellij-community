// "Surround with try/catch" "true-preview"
import java.io.IOException;

class C {
    static final String S;

    static {
        try {
            S = getString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String getString() throws IOException {
        if(Math.random() > 0.5) throw new IOException();
        return "foo";
    }
}
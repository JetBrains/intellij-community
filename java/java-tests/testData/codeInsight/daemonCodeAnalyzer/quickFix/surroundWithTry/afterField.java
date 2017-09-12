// "Surround with try/catch" "true"
import java.io.IOException;

class C {
    static final String S;

    static {
        try {
            S = getString();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String getString() throws IOException {
        if(Math.random() > 0.5) throw new IOException();
        return "foo";
    }
}
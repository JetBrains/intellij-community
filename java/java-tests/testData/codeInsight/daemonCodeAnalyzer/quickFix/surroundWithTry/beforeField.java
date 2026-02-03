// "Surround with try/catch" "true-preview"
import java.io.IOException;

class C {
    static final String S = getSt<caret>ring();

    static String getString() throws IOException {
        if(Math.random() > 0.5) throw new IOException();
        return "foo";
    }
}
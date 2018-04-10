// "Replace explicit type with 'var'" "true"
import java.io.*;
class Main {
    void m(InputStream s) {
        try (<caret>InputStream in = s) {}
    }
}
// "Replace explicit type with 'var'" "true-preview"
import java.io.*;
class Main {
    void m(InputStream s) {
        try (var in = s) {}
    }
}
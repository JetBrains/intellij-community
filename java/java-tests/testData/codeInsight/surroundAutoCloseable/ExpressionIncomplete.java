import java.io.*;

class C {
    void m(File file) throws IOException {
        new FileInputStream(file)<caret>
    }
}
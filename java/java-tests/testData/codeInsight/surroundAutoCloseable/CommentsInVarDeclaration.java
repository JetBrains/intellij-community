import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class C {
    void m(File file) throws IOException {
        <caret>FileInputStream fileInputStream = new FileInputStream(file);
        String s = "initial value";//Non-NLS
        String bar = s + fileInputStream.read();
    }
}

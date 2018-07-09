import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class C {
    void m(File file) throws IOException {
        String s;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            //Non-NLS
            s = "initial value";
            s = s + fileInputStream.read();
        }
        s += "end";
    }
}

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class C {
    void m(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            int read;
            do {
                read = fileInputStream.read();
                System.out.println(read);
            }
            while (read != -1);
        }
    }
}

import java.io.*;

class C {
    void m(File file) throws IOException {
        //comment after expr
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
        }
    }
}
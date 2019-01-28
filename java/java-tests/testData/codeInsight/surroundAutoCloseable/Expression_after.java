import java.io.*;

class C {
    void m(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
        }//comment after expr
    }
}
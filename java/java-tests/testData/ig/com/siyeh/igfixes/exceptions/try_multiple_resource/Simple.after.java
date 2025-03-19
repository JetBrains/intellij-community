import java.io.*;
class Simple {
    void foo(File file1, File file2) throws IOException {
        try (FileInputStream in = new FileInputStream(file1)) {
            try (FileOutputStream out = new FileOutputStream(file2)) {
                System.out.println(in + ", " + out);
            }
        }
    }
}
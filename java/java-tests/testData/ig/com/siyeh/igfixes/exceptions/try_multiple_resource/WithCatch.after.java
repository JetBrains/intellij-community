import java.io.*;
class C {
    void foo(File file1, File file2) {
        try (FileInputStream in = new FileInputStream(file1)) {
            try (FileOutputStream out = new FileOutputStream(file2)) {
                System.out.println(in + ", " + out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
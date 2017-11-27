import java.io.*;

class C {
    void m(File file) throws IOException {
        <caret>new FileInputStream(file);//comment after expr
    }
}
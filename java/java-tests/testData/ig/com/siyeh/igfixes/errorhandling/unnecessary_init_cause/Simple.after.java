import java.io.*;

class Simple {

    void foo() {
        try {
            new FileInputStream("asdf");
        } catch (FileNotFoundException e) {
            throw (RuntimeException) new RuntimeException(e);
        }
    }
}
import java.io.*;

class Indirect {

    void foo() {
        try {
            new FileInputStream("asdf");
        } catch (FileNotFoundException e) {
            final RuntimeException exception = new RuntimeException(e);
            throw exception;
        }
    }
}
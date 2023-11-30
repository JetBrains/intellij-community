import java.io.*;

class Indirect {

    void foo() {
        try {
            new FileInputStream("asdf");
        } catch (FileNotFoundException e) {
            final RuntimeException exception = new RuntimeException();
            exception.<caret>initCause(e);
            throw exception;
        }
    }
}
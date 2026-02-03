import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class Foo {

    void test(File f) throws IOException {
        try(new <caret>FileInputStream(f)) {
        }
    }
}
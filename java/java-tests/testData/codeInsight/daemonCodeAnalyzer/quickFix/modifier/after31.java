// "Make 'c class initializer' not static" "true-preview"
import java.io.*;

class c {
    void f() {}

    {
        <caret>f();
    }
}

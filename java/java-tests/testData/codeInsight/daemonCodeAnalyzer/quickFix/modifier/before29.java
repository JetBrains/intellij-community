// "Make 'c class initializer' not static" "true-preview"
import java.io.*;

class c {
    class inner {
        class ininner {}
    }

    static {
        <caret>new inner();
    }
}

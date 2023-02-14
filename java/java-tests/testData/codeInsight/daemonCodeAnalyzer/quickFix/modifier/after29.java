// "Make 'c class initializer' not static" "true-preview"
import java.io.*;

class c {
    class inner {
        class ininner {}
    }

    {
        <caret>new inner();
    }
}

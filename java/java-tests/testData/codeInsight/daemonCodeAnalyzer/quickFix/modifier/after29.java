// "Make 'c class initializer' not static" "true"
import java.io.*;

class c {
    class inner {
        class ininner {}
    }

    {
        <caret>new inner();
    }
}

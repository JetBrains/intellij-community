// "Make 'inner' static" "true"
import java.io.*;

class c {
    static class inner {
        class ininner {}
    }

    static {
        <caret>new inner();
    }
}

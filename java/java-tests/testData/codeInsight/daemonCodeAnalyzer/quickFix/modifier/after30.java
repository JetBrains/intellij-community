// "Make 'inner' static" "true-preview"
import java.io.*;

class c {
    static class inner {
        class ininner {}
    }

    static {
        <caret>new inner();
    }
}

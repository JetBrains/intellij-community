import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

class C {
    void m(File file) throws IOException {
        <caret>FileInputStream stream = new FileInputStream(file);
        int x;
        FileChannel ch = stream.getChannel();
        ch.close();
        x = 0;
    }
}

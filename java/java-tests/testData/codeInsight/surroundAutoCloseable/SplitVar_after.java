import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

class C {
    void m(File file) throws IOException {
        int x;
        FileChannel ch;
        try (FileInputStream stream = new FileInputStream(file)) {
            ch = stream.getChannel();
        }
        ch.close();
        x = 0;
    }
}

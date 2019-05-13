import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

class C {
    void m(File file) throws IOException {
        <caret>FileInputStream stream = new FileInputStream(file);
        stream.getFD();
        FileChannel channel1 = stream.getChannel(), channel2 = stream.getChannel();
        channel1.close();
        channel2.close();
    }
}

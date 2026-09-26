
import java.io.IOException;

class MyTest {

    private static void switchChain(final int i) throws IOException {
        int g = switch (i) {
            default -> {
                String temp = "";
                throw new IOException(temp);
            }
        };

    }
}
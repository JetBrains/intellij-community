import java.io.IOException;

class Test {
    String test(boolean b) throws IOException {
        if (b) {
            return "";
        }
        <selection>throw new IOException();</selection>
    }
}

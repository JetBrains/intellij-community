// "Move 'return' closer to computation of the value of 's'" "true"
import java.io.*;

class T {
    private static String getString() throws IOException {
        String s;
        try (BufferedReader r = open()) {
            s = r.readLine();
        }
        re<caret>turn s;
    }

    private static BufferedReader open() throws FileNotFoundException {
        return null;
    }
}
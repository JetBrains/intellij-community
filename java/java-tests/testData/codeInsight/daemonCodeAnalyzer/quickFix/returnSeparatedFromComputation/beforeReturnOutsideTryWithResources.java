// "Move 'return' to computation of the value of 'n'" "true"
import java.io.*;

class T {
    private static String getString() throws IOException {
        String s;
        try (BufferedReader r = open()) {
            s = r.readLine();
        }
        re<return>turn s;
    }

    private static BufferedReader open() throws FileNotFoundException {
        return null;
    }
}
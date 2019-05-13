// "Move 'return' closer to computation of the value of 's'" "true"
import java.io.*;

class T {
    private static String getString() throws IOException {
        try (BufferedReader r = open()) {
            return r.readLine();
        }
    }

    private static BufferedReader open() throws FileNotFoundException {
        return null;
    }
}
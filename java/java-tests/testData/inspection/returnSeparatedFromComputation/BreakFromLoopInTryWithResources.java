import java.io.*;

class T {
    private static String getString() throws IOException {
        String s;
        try (BufferedReader reader = open()) {
            while (true) {
                s = reader.readLine();
                if (s == null || s.startsWith("$")) {
                    break;
                }
            }
        }
        <warning descr="Return separated from computation of value of 's'">return s;</warning>
    }
    private static BufferedReader open() throws FileNotFoundException {
        return null;
    }
}

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Test {
    public int test(int x) {
        int y = 42;
        try {
            y = getY(x, y);
        } catch (FileNotFoundException e) {
        }
        return y;
    }

    private static int getY(int x, int y) throws FileNotFoundException {
        new Scanner(new File("file.txt"));
        y = y + x;
        y = y / x;
        return y;
    }
}
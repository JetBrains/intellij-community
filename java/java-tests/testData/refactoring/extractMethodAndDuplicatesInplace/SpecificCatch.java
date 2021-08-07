import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Test {
    public int test(int x) {
        int y = 42;
        try {
          <selection>new Scanner(new File("file.txt"));
          y = y + x;
          y = y / x;</selection>
        } catch (FileNotFoundException e) {
        }
        return y;
    }
}
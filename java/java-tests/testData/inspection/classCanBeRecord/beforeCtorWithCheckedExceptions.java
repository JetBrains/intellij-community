// "Convert to record class" "false"
import java.io.*;

class <caret>R {
    final int first;
    final int second;

    private R(int first, int second) throws FileNotFoundException {
        this.first = first;
        this.second = second;
    }
}
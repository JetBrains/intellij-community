// "Convert to record class" "true-preview"
import java.io.*;

class <caret>R {
    final int first;

    private R(int first) {
        this.first = first;
    }

    int getFirst() throws FileNotFoundException {
        return first;
    }
}

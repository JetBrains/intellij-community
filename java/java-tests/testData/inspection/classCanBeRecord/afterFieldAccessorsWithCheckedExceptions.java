// "Convert to record class" "true-preview"
import java.io.*;

record R(int first) {

    int getFirst() throws FileNotFoundException {
        return first;
    }
}

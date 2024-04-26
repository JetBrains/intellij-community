// "Convert to record class" "true"
import java.io.*;

record R(int first) {

    int getFirst() throws FileNotFoundException {
        return first;
    }
}
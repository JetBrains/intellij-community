// "Convert to a record" "true"
import java.io.*;

record R(int first) {

    int getFirst() throws FileNotFoundException {
        return first;
    }
}
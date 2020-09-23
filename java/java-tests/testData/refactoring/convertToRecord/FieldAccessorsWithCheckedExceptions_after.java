import java.io.*;

record R(int first) {
    R(int first) {
        this.first = first;
    }

    int getFirst() throws FileNotFoundException {
        return first;
    }
}
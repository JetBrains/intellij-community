// "Remove field 'i'" "true"
import java.io.*;

class a {
    private int <caret>i;
    private void run() {
        int j;
        int i;
    }
}


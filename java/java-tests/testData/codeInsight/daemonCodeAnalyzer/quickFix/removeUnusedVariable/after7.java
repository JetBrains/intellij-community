// "Remove variable 'i'" "true"
import java.io.*;

class a {
    int k;
    private void run() {
        <caret>while (1 > 0) ;
        for (;;) ;
    }
}


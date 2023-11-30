// "Remove local variable 'i'" "true-preview"
import java.io.*;

class a {
    int k;
    private void run() {
        k = 9;
        if ((k = 9) == 0) {
            k = 8;
        }
        while (1 > 0) ;
        for (;; ) ;
    }
}


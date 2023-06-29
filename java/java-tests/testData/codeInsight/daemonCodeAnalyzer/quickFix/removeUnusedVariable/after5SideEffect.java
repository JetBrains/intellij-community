// "Remove field 'i'" "true-preview"
import java.io.*;

class a {
    <caret>private void run() {
        int j;
        int k = 9;
        if ((k = 9) == 0) {
            k = 8;
        }
        if (3 ==0) ;
    }
}


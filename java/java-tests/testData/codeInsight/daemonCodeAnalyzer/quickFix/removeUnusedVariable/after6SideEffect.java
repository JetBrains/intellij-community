// "Remove local variable 'i'" "true-preview"
import java.io.*;

class a {
    private int run() {
        <caret>int j;
        int k = 9;
        if ((k = 9) == 0) {
            k = 8;
        }
        if (3 ==0) ;
        else return (k);
        return 0;
    }
}

